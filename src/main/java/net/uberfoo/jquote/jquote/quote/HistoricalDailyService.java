package net.uberfoo.jquote.jquote.quote;

import com.pangility.schwab.api.client.common.ApiUnauthorizedException;
import com.pangility.schwab.api.client.marketdata.SymbolNotFoundException;
import com.pangility.schwab.api.client.marketdata.model.pricehistory.Candle;
import com.pangility.schwab.api.client.marketdata.model.pricehistory.FrequencyType;
import com.pangility.schwab.api.client.marketdata.model.pricehistory.PeriodType;
import com.pangility.schwab.api.client.marketdata.model.pricehistory.PriceHistoryRequest;
import com.pangility.schwab.api.client.marketdata.model.pricehistory.PriceHistoryResponse;
import com.pangility.schwab.api.client.oauth2.RefreshTokenException;
import net.uberfoo.jquote.jquote.schwab.AuthBrowserLauncher;
import net.uberfoo.jquote.jquote.schwab.SchwabSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Service
public class HistoricalDailyService {
    private static final Logger log = LoggerFactory.getLogger(HistoricalDailyService.class);
    private static final ZoneId MARKET_TIME_ZONE = ZoneId.of("America/New_York");
    private static final String SCHWAB_PRICE_HISTORY_ENDPOINT = "/marketdata/v1/pricehistory";
    private static final int MAX_ERROR_PAYLOAD_LENGTH = 2_000;
    private static final Pattern SENSITIVE_TOKEN_PATTERN = Pattern.compile(
            "(?i)\"?(access_token|refresh_token|id_token|authorization|client_secret)\"?\\s*[:=]\\s*\"?[^\",\\s]+\"?");

    private final SchwabSessionService sessionService;
    private final AuthBrowserLauncher authBrowserLauncher;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final AtomicReference<String> activeSymbol = new AtomicReference<>("");
    private final AtomicLong latestRequestId = new AtomicLong();

    @Autowired
    public HistoricalDailyService(SchwabSessionService sessionService,
                                  AuthBrowserLauncher authBrowserLauncher,
                                  ApplicationEventPublisher eventPublisher) {
        this(sessionService, authBrowserLauncher, eventPublisher, Clock.system(MARKET_TIME_ZONE));
    }

    HistoricalDailyService(SchwabSessionService sessionService,
                           AuthBrowserLauncher authBrowserLauncher,
                           ApplicationEventPublisher eventPublisher,
                           Clock clock) {
        this.sessionService = sessionService;
        this.authBrowserLauncher = authBrowserLauncher;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public void requestDailyHistory(String symbol, HistoricalRangePreset preset) {
        HistoricalRangePreset resolvedPreset = preset == null ? HistoricalRangePreset.DAYS_30 : preset;
        LocalDate today = LocalDate.now(clock);
        HistoricalDateRange range = resolvedPreset.toDateRange(today);
        requestDailyHistory(symbol, range.startDate(), range.endDate());
    }

    public void requestDailyHistory(String symbol, LocalDate startDate, LocalDate endDate) {
        String normalized = normalizeSymbol(symbol);
        HistoricalDateRange range = new HistoricalDateRange(startDate, endDate);
        Optional<String> rangeError = HistoricalDateRange.validate(startDate, endDate);
        if (normalized.isEmpty()) {
            publishError(symbol, range, "Symbol is required.");
            return;
        }
        if (rangeError.isPresent()) {
            publishError(normalized, range, rangeError.get());
            return;
        }

        activeSymbol.set(normalized);
        long requestId = latestRequestId.incrementAndGet();
        fetchAndPublish(normalized, range, requestId);
    }

    PriceHistoryRequest buildRequest(String symbol, HistoricalDateRange range) {
        return PriceHistoryRequest.builder()
                .withSymbol(symbol)
                .withPeriodType(PeriodType.year)
                .withFrequencyType(FrequencyType.daily)
                .withFrequency(1)
                .withStartDate(range.startDate())
                .withEndDate(range.endDate())
                .withNeedExtendedHoursData(Boolean.FALSE)
                .withNeedPreviousClose(Boolean.FALSE)
                .build();
    }

    HistoricalDailySnapshot toSnapshot(PriceHistoryResponse response, String symbol, HistoricalDateRange range) {
        List<Candle> candles = response.getCandles() == null ? Collections.emptyList() : response.getCandles();
        List<DailyOhlcPoint> points = candles.stream()
                .map(candle -> new DailyOhlcPoint(
                        resolveDate(candle),
                        candle.getOpen(),
                        candle.getHigh(),
                        candle.getLow(),
                        candle.getClose(),
                        candle.getVolume()))
                .filter(this::isCompletePoint)
                .sorted(Comparator.comparing(DailyOhlcPoint::date))
                .toList();
        return new HistoricalDailySnapshot(symbol, range, points);
    }

    private void fetchAndPublish(String symbol, HistoricalDateRange range, long requestId) {
        if (!sessionService.hasValidRefreshToken()) {
            authBrowserLauncher.launchIfNeeded();
            publishError(symbol, range, notAuthorizedMessage());
            return;
        }

        PriceHistoryRequest request = buildRequest(symbol, range);
        log.info("historical_daily_request_start requestId={} endpoint={} symbol={} startDate={} endDate={} frequencyType={} frequency={} needExtendedHoursData={}",
                requestId,
                SCHWAB_PRICE_HISTORY_ENDPOINT,
                request.getSymbol(),
                request.getStartDate(),
                request.getEndDate(),
                request.getFrequencyType(),
                request.getFrequency(),
                request.getNeedExtendedHoursData());

        Mono<HistoricalDailySnapshot> snapshotMono = sessionService.client()
                .fetchPriceHistoryToMono(request)
                .map(response -> toSnapshot(response, symbol, range));

        snapshotMono.subscribe(
                snapshot -> {
                    if (!isActiveRequest(snapshot.symbol(), requestId)) {
                        return;
                    }
                    eventPublisher.publishEvent(new HistoricalDailyUpdateEvent(snapshot));
                },
                error -> {
                    if (!isActiveRequest(symbol, requestId)) {
                        return;
                    }
                    Optional<BadRequestDiagnostics> diagnostics = extractBadRequestDiagnostics(error);
                    diagnostics.ifPresent(value -> log.warn(
                            "historical_daily_bad_request requestId={} endpoint={} symbol={} startDate={} endDate={} frequencyType={} frequency={} needExtendedHoursData={} status={} responseBody={}",
                            requestId,
                            SCHWAB_PRICE_HISTORY_ENDPOINT,
                            request.getSymbol(),
                            request.getStartDate(),
                            request.getEndDate(),
                            request.getFrequencyType(),
                            request.getFrequency(),
                            request.getNeedExtendedHoursData(),
                            value.statusCode(),
                            redactAndLimitPayload(value.payload())));
                    log.warn("historical_daily_request_failed requestId={} endpoint={} symbol={} startDate={} endDate={}",
                            requestId,
                            SCHWAB_PRICE_HISTORY_ENDPOINT,
                            request.getSymbol(),
                            request.getStartDate(),
                            request.getEndDate(),
                            error);
                    if (isAuthError(error)) {
                        authBrowserLauncher.launch();
                    }
                    if (diagnostics.isPresent()) {
                        publishError(symbol, range, badRequestMessage());
                        return;
                    }
                    publishError(symbol, range, toMessage(error));
                }
        );
    }

    private boolean isCompletePoint(DailyOhlcPoint point) {
        return point.date() != null
                && point.open() != null
                && point.high() != null
                && point.low() != null
                && point.close() != null;
    }

    private LocalDate resolveDate(Candle candle) {
        if (candle.getDatetimeISO8601() != null) {
            return candle.getDatetimeISO8601().toLocalDate();
        }
        if (candle.getDatetime() != null) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(candle.getDatetime()), MARKET_TIME_ZONE).toLocalDate();
        }
        return null;
    }

    private boolean isActiveRequest(String symbol, long requestId) {
        return requestId == latestRequestId.get() && symbol.equalsIgnoreCase(activeSymbol.get());
    }

    private void publishError(String symbol, HistoricalDateRange range, String message) {
        eventPublisher.publishEvent(new HistoricalDailyErrorEvent(symbol, range, message));
    }

    private String toMessage(Throwable error) {
        if (error instanceof RefreshTokenException || error instanceof ApiUnauthorizedException) {
            return notAuthorizedMessage();
        }
        if (error instanceof SymbolNotFoundException) {
            return "Symbol not found.";
        }
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return "Failed to fetch historical daily data.";
        }
        return "Failed to fetch historical daily data: " + message;
    }

    private boolean isAuthError(Throwable error) {
        return error instanceof RefreshTokenException || error instanceof ApiUnauthorizedException;
    }

    private String notAuthorizedMessage() {
        return "Schwab authorization required. If a browser didn't open, visit " + sessionService.authStartUrl() + ".";
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null) {
            return "";
        }
        return symbol.trim().toUpperCase();
    }

    Optional<BadRequestDiagnostics> extractBadRequestDiagnostics(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof WebClientResponseException webClientResponseException) {
                int statusCode = webClientResponseException.getStatusCode().value();
                if (statusCode == 400) {
                    return Optional.of(new BadRequestDiagnostics(statusCode, webClientResponseException.getResponseBodyAsString()));
                }
            } else if (current instanceof ResponseStatusException responseStatusException) {
                int statusCode = responseStatusException.getStatusCode().value();
                if (statusCode == 400) {
                    return Optional.of(new BadRequestDiagnostics(statusCode, responseStatusException.getReason()));
                }
            }
            current = current.getCause();
        }
        return Optional.empty();
    }

    String redactAndLimitPayload(String payload) {
        String normalized = payload == null || payload.isBlank() ? "<empty>" : payload.replaceAll("[\\r\\n]+", " ").trim();
        String redacted = SENSITIVE_TOKEN_PATTERN.matcher(normalized).replaceAll("$1=<redacted>");
        if (redacted.length() <= MAX_ERROR_PAYLOAD_LENGTH) {
            return redacted;
        }
        return redacted.substring(0, MAX_ERROR_PAYLOAD_LENGTH) + "...(truncated)";
    }

    private String badRequestMessage() {
        return "Historical request was rejected (400). Check symbol and date range, then try again.";
    }

    record BadRequestDiagnostics(int statusCode, String payload) {
    }
}
