package net.uberfoo.jquote.jquote.quote;

import com.pangility.schwab.api.client.common.ApiUnauthorizedException;
import com.pangility.schwab.api.client.marketdata.SymbolNotFoundException;
import com.pangility.schwab.api.client.marketdata.model.quotes.QuoteResponse;
import com.pangility.schwab.api.client.marketdata.model.quotes.equity.QuoteEquity;
import com.pangility.schwab.api.client.oauth2.RefreshTokenException;
import net.uberfoo.jquote.jquote.schwab.AuthBrowserLauncher;
import net.uberfoo.jquote.jquote.schwab.SchwabSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class QuoteService {
    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);
    private static final ZoneId US_EASTERN = ZoneId.of("America/New_York");
    private static final LocalTime US_REGULAR_OPEN = LocalTime.of(9, 30);
    private static final LocalTime US_REGULAR_CLOSE = LocalTime.of(16, 0);

    private final SchwabSessionService sessionService;
    private final AuthBrowserLauncher authBrowserLauncher;
    private final ApplicationEventPublisher eventPublisher;
    private final AtomicReference<String> activeSymbol = new AtomicReference<>();

    public QuoteService(SchwabSessionService sessionService,
                        AuthBrowserLauncher authBrowserLauncher,
                        ApplicationEventPublisher eventPublisher) {
        this.sessionService = sessionService;
        this.authBrowserLauncher = authBrowserLauncher;
        this.eventPublisher = eventPublisher;
    }

    public void requestQuote(String symbol) {
        String normalized = normalizeSymbol(symbol);
        if (normalized.isEmpty()) {
            publishError(symbol, "Symbol is required.");
            return;
        }
        activeSymbol.set(normalized);
        fetchAndPublish(normalized);
    }

    @Scheduled(fixedDelayString = "${jquote.quote.poll-interval-ms:60000}")
    public void refreshQuote() {
        String symbol = activeSymbol.get();
        if (symbol == null || symbol.isBlank()) {
            return;
        }
        fetchAndPublish(symbol);
    }

    private void fetchAndPublish(String symbol) {
        if (!sessionService.hasValidRefreshToken()) {
            authBrowserLauncher.launchIfNeeded();
            publishError(symbol, notAuthorizedMessage());
            return;
        }
        Mono<QuoteSnapshot> quoteSnapshotMono = sessionService.client()
                .fetchQuoteToMono(symbol)
                .map(this::toSnapshot);

        quoteSnapshotMono.subscribe(
                snapshot -> eventPublisher.publishEvent(new QuoteUpdateEvent(snapshot)),
                error -> {
                    log.warn("Quote fetch failed for {}", symbol, error);
                    if (isAuthError(error)) {
                        authBrowserLauncher.launch();
                    }
                    publishError(symbol, toMessage(error));
                }
        );
    }

    private QuoteSnapshot toSnapshot(QuoteResponse response) {
        BigDecimal lastPrice = null;
        BigDecimal change = null;
        BigDecimal percentChange = null;
        BigDecimal openPrice = null;
        BigDecimal closePrice = null;
        BigDecimal latestTickPrice = null;
        boolean marketOpen = false;

        if (response instanceof QuoteResponse.EquityResponse equityResponse) {
            if (equityResponse.getRegular() != null) {
                lastPrice = equityResponse.getRegular().getRegularMarketLastPrice();
                change = equityResponse.getRegular().getRegularMarketNetChange();
                percentChange = equityResponse.getRegular().getRegularMarketPercentChange();
                latestTickPrice = equityResponse.getRegular().getRegularMarketLastPrice();
            }
            QuoteEquity quote = equityResponse.getQuote();
            if (quote != null) {
                if (lastPrice == null) {
                    lastPrice = quote.getLastPrice();
                }
                if (change == null) {
                    change = quote.getNetChange();
                }
                if (percentChange == null) {
                    percentChange = quote.getNetPercentChange();
                }
                if (latestTickPrice == null) {
                    latestTickPrice = quote.getLastPrice();
                }
                openPrice = quote.getOpenPrice();
                closePrice = quote.getClosePrice();
            }
            marketOpen = isUsRegularSessionOpenNow();
        } else if (response instanceof QuoteResponse.IndexResponse indexResponse) {
            if (indexResponse.getQuote() != null) {
                lastPrice = indexResponse.getQuote().getLastPrice();
                change = indexResponse.getQuote().getNetChange();
                percentChange = indexResponse.getQuote().getNetPercentChange();
            }
        } else if (response instanceof QuoteResponse.ForexResponse forexResponse) {
            if (forexResponse.getQuote() != null) {
                lastPrice = forexResponse.getQuote().getLastPrice();
                change = forexResponse.getQuote().getNetChange();
                percentChange = forexResponse.getQuote().getNetPercentChange();
            }
        } else if (response instanceof QuoteResponse.FutureResponse futureResponse) {
            if (futureResponse.getQuote() != null) {
                lastPrice = futureResponse.getQuote().getLastPrice();
                change = futureResponse.getQuote().getNetChange();
                percentChange = futureResponse.getQuote().getNetPercentChange();
            }
        } else if (response instanceof QuoteResponse.FutureOptionResponse futureOptionResponse) {
            if (futureOptionResponse.getQuote() != null) {
                lastPrice = futureOptionResponse.getQuote().getLastPrice();
                change = futureOptionResponse.getQuote().getNetChange();
                percentChange = futureOptionResponse.getQuote().getNetPercentChange();
            }
        } else if (response instanceof QuoteResponse.OptionResponse optionResponse) {
            if (optionResponse.getQuote() != null) {
                lastPrice = optionResponse.getQuote().getLastPrice();
                change = optionResponse.getQuote().getNetChange();
                percentChange = optionResponse.getQuote().getNetPercentChange();
            }
        }

        return new QuoteSnapshot(
                response.getSymbol(),
                response.getAssetMainType(),
                lastPrice,
                change,
                percentChange,
                openPrice,
                closePrice,
                latestTickPrice,
                marketOpen
        );
    }

    private void publishError(String symbol, String message) {
        eventPublisher.publishEvent(new QuoteErrorEvent(symbol, message));
    }

    private String toMessage(Throwable error) {
        if (error instanceof RefreshTokenException) {
            return notAuthorizedMessage();
        }
        if (error instanceof ApiUnauthorizedException) {
            return notAuthorizedMessage();
        }
        if (error instanceof SymbolNotFoundException) {
            return "Symbol not found.";
        }
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return "Failed to fetch quote.";
        }
        return "Failed to fetch quote: " + message;
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

    private boolean isUsRegularSessionOpenNow() {
        ZonedDateTime easternNow = ZonedDateTime.now(US_EASTERN);
        DayOfWeek day = easternNow.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = easternNow.toLocalTime();
        return !time.isBefore(US_REGULAR_OPEN) && time.isBefore(US_REGULAR_CLOSE);
    }
}
