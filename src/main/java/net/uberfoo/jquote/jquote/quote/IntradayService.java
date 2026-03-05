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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class IntradayService {
    private static final Logger log = LoggerFactory.getLogger(IntradayService.class);
    private static final ZoneId MARKET_TIME_ZONE = ZoneId.of("America/New_York");
    private static final LocalTime US_REGULAR_OPEN = LocalTime.of(9, 30);
    private static final LocalTime US_REGULAR_CLOSE = LocalTime.of(16, 0);
    private static final int INTRADAY_FIVE_MINUTE_FREQUENCY = 5;

    private final SchwabSessionService sessionService;
    private final AuthBrowserLauncher authBrowserLauncher;
    private final ApplicationEventPublisher eventPublisher;
    private final AtomicReference<String> activeSymbol = new AtomicReference<>("");
    private final AtomicReference<IntradaySession> activeSession = new AtomicReference<>(IntradaySession.REGULAR);
    private final AtomicLong latestRequestId = new AtomicLong();

    public IntradayService(SchwabSessionService sessionService,
                           AuthBrowserLauncher authBrowserLauncher,
                           ApplicationEventPublisher eventPublisher) {
        this.sessionService = sessionService;
        this.authBrowserLauncher = authBrowserLauncher;
        this.eventPublisher = eventPublisher;
    }

    public void requestIntraday(String symbol, IntradaySession intradaySession) {
        String normalized = normalizeSymbol(symbol);
        if (normalized.isEmpty()) {
            publishError(symbol, resolvedSession(intradaySession), "Symbol is required.");
            return;
        }
        IntradaySession session = resolvedSession(intradaySession);
        activeSymbol.set(normalized);
        activeSession.set(session);
        long requestId = latestRequestId.incrementAndGet();
        fetchAndPublish(normalized, session, requestId);
    }

    @Scheduled(fixedDelayString = "${jquote.intraday.poll-interval-ms:60000}")
    public void refreshIntraday() {
        if (!isUsRegularSessionOpenNow()) {
            return;
        }
        String symbol = activeSymbol.get();
        if (symbol == null || symbol.isBlank()) {
            return;
        }
        IntradaySession session = activeSession.get();
        long requestId = latestRequestId.incrementAndGet();
        fetchAndPublish(symbol, session, requestId);
    }

    private void fetchAndPublish(String symbol, IntradaySession session, long requestId) {
        if (!sessionService.hasValidRefreshToken()) {
            authBrowserLauncher.launchIfNeeded();
            publishError(symbol, session, notAuthorizedMessage());
            return;
        }

        Mono<IntradaySnapshot> intradaySnapshotMono = sessionService.client()
                .fetchPriceHistoryToMono(buildRequest(symbol, session))
                .map(response -> toSnapshot(response, symbol, session));

        intradaySnapshotMono.subscribe(
                snapshot -> {
                    if (!isActiveRequest(snapshot.symbol(), snapshot.session(), requestId)) {
                        return;
                    }
                    eventPublisher.publishEvent(new IntradayUpdateEvent(snapshot));
                },
                error -> {
                    if (!isActiveRequest(symbol, session, requestId)) {
                        return;
                    }
                    log.warn("Intraday fetch failed for {} session={}", symbol, session, error);
                    if (isAuthError(error)) {
                        authBrowserLauncher.launch();
                    }
                    publishError(symbol, session, toMessage(error));
                }
        );
    }

    PriceHistoryRequest buildRequest(String symbol, IntradaySession session) {
        LocalDate todayEastern = LocalDate.now(MARKET_TIME_ZONE);
        return PriceHistoryRequest.builder()
                .withSymbol(symbol)
                .withPeriodType(PeriodType.day)
                .withPeriod(1)
                .withFrequencyType(FrequencyType.minute)
                .withFrequency(INTRADAY_FIVE_MINUTE_FREQUENCY)
                .withStartDate(todayEastern)
                .withEndDate(todayEastern)
                .withNeedExtendedHoursData(session.includeExtendedHours())
                .withNeedPreviousClose(Boolean.FALSE)
                .build();
    }

    IntradaySnapshot toSnapshot(PriceHistoryResponse response, String symbol, IntradaySession session) {
        List<Candle> candles = response.getCandles() == null ? Collections.emptyList() : response.getCandles();
        List<IntradayPoint> points = candles.stream()
                .map(candle -> new IntradayPoint(
                        resolveTimestamp(candle),
                        candle.getOpen(),
                        candle.getHigh(),
                        candle.getLow(),
                        candle.getClose(),
                        candle.getVolume()))
                .filter(this::isCompletePoint)
                .sorted(Comparator.comparing(IntradayPoint::timestamp))
                .toList();
        boolean extendedHoursSupported = detectExtendedHoursSupport(response, session, points);

        if (points.isEmpty()) {
            if (isUsTradingDay(LocalDate.now(MARKET_TIME_ZONE)) && !(session.includeExtendedHours() && !extendedHoursSupported)) {
                throw new IllegalStateException("Intraday data is unavailable.");
            }
            return new IntradaySnapshot(symbol, session, extendedHoursSupported, points);
        }
        return new IntradaySnapshot(symbol, session, extendedHoursSupported, points);
    }

    private LocalDateTime resolveTimestamp(Candle candle) {
        if (candle.getDatetimeISO8601() != null) {
            return candle.getDatetimeISO8601();
        }
        if (candle.getDatetime() != null) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(candle.getDatetime()), MARKET_TIME_ZONE);
        }
        return null;
    }

    private boolean isActiveRequest(String symbol, IntradaySession session, long requestId) {
        return requestId == latestRequestId.get()
                && symbol.equalsIgnoreCase(activeSymbol.get())
                && session == activeSession.get();
    }

    private boolean isCompletePoint(IntradayPoint point) {
        return point.timestamp() != null
                && point.open() != null
                && point.high() != null
                && point.low() != null
                && point.close() != null;
    }

    private boolean detectExtendedHoursSupport(PriceHistoryResponse response,
                                               IntradaySession session,
                                               List<IntradayPoint> points) {
        if (!session.includeExtendedHours()) {
            return false;
        }
        if (Boolean.TRUE.equals(response.getEmpty()) || points.isEmpty()) {
            return false;
        }
        return points.stream()
                .map(IntradayPoint::timestamp)
                .anyMatch(this::isExtendedHoursTimestamp);
    }

    private boolean isExtendedHoursTimestamp(LocalDateTime timestamp) {
        LocalTime time = timestamp.toLocalTime();
        return time.isBefore(US_REGULAR_OPEN) || !time.isBefore(US_REGULAR_CLOSE);
    }

    private void publishError(String symbol, IntradaySession session, String message) {
        eventPublisher.publishEvent(new IntradayErrorEvent(symbol, session, message));
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
            return "Failed to fetch intraday data.";
        }
        return "Failed to fetch intraday data: " + message;
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

    private IntradaySession resolvedSession(IntradaySession intradaySession) {
        return intradaySession == null ? IntradaySession.REGULAR : intradaySession;
    }

    private boolean isUsTradingDay(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }

    private boolean isUsRegularSessionOpenNow() {
        ZonedDateTime easternNow = ZonedDateTime.now(MARKET_TIME_ZONE);
        DayOfWeek day = easternNow.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = easternNow.toLocalTime();
        return !time.isBefore(US_REGULAR_OPEN) && time.isBefore(US_REGULAR_CLOSE);
    }
}
