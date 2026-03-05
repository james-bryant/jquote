package net.uberfoo.jquote.jquote.quote;

import com.pangility.schwab.api.client.marketdata.SchwabMarketDataApiClient;
import com.pangility.schwab.api.client.marketdata.model.pricehistory.Candle;
import com.pangility.schwab.api.client.marketdata.model.pricehistory.FrequencyType;
import com.pangility.schwab.api.client.marketdata.model.pricehistory.PeriodType;
import com.pangility.schwab.api.client.marketdata.model.pricehistory.PriceHistoryResponse;
import net.uberfoo.jquote.jquote.schwab.AuthBrowserLauncher;
import net.uberfoo.jquote.jquote.schwab.SchwabSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoricalDailyServiceTest {
    @Test
    void mapsPresetRangesToExpectedStartAndEnd() {
        LocalDate endDate = LocalDate.of(2026, 3, 3);

        HistoricalDateRange range30 = HistoricalRangePreset.DAYS_30.toDateRange(endDate);
        HistoricalDateRange range90 = HistoricalRangePreset.DAYS_90.toDateRange(endDate);
        HistoricalDateRange range120 = HistoricalRangePreset.DAYS_120.toDateRange(endDate);

        assertEquals(LocalDate.of(2026, 2, 2), range30.startDate());
        assertEquals(endDate, range30.endDate());
        assertEquals(LocalDate.of(2025, 12, 4), range90.startDate());
        assertEquals(endDate, range90.endDate());
        assertEquals(LocalDate.of(2025, 11, 4), range120.startDate());
        assertEquals(endDate, range120.endDate());
    }

    @Test
    void acceptsCustomDateRangeWithoutMaxRangeValidation() {
        LocalDate startDate = LocalDate.of(2000, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 3, 3);

        assertTrue(HistoricalDateRange.validate(startDate, endDate).isEmpty());
    }

    @Test
    void rejectsInvalidDateRangeWithUserFacingValidationMessage() {
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        HistoricalDailyService service = createService(eventPublisher);

        service.requestDailyHistory("AAPL", LocalDate.of(2026, 3, 3), LocalDate.of(2026, 3, 2));

        assertEquals(1, eventPublisher.events().size());
        HistoricalDailyErrorEvent event = assertInstanceOf(
                HistoricalDailyErrorEvent.class,
                eventPublisher.events().getFirst()
        );
        assertEquals("AAPL", event.symbol());
        assertEquals("Start date must be on or before end date.", event.message());
    }

    @Test
    void buildsDailyRequestUsingProvidedDateRange() {
        HistoricalDailyService service = createService(new CapturingEventPublisher());
        HistoricalDateRange range = new HistoricalDateRange(LocalDate.of(2025, 12, 1), LocalDate.of(2026, 3, 3));

        var request = service.buildRequest("MSFT", range);

        assertEquals("MSFT", request.getSymbol());
        assertEquals(PeriodType.year, request.getPeriodType());
        assertEquals(FrequencyType.daily, request.getFrequencyType());
        assertEquals(1, request.getFrequency());
        assertEquals(range.startDate(), request.getStartDate());
        assertEquals(range.endDate(), request.getEndDate());
    }

    @Test
    void normalizesCandleResponseToSortedOhlcPoints() {
        HistoricalDailyService service = createService(new CapturingEventPublisher());
        HistoricalDateRange range = new HistoricalDateRange(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3));
        Candle candleMar3 = candle(LocalDateTime.of(2026, 3, 3, 0, 0), 100, 105, 99, 103, 5000L);
        Candle candleMar1 = candle(LocalDateTime.of(2026, 3, 1, 0, 0), 98, 102, 97, 100, 4500L);
        Candle incomplete = candle(LocalDateTime.of(2026, 3, 2, 0, 0), 99, 103, 98, 101, 4600L);
        incomplete.setOpen(null);
        PriceHistoryResponse response = new PriceHistoryResponse();
        response.setCandles(List.of(candleMar3, incomplete, candleMar1));

        HistoricalDailySnapshot snapshot = service.toSnapshot(response, "AAPL", range);

        assertEquals("AAPL", snapshot.symbol());
        assertEquals(range, snapshot.range());
        assertEquals(2, snapshot.points().size());
        assertEquals(LocalDate.of(2026, 3, 1), snapshot.points().get(0).date());
        assertEquals(LocalDate.of(2026, 3, 3), snapshot.points().get(1).date());
        assertEquals(new BigDecimal("103"), snapshot.points().get(1).close());
    }

    @Test
    void extractsBadRequestDiagnosticsFromWebClientResponseException() {
        HistoricalDailyService service = createService(new CapturingEventPublisher());
        WebClientResponseException error = WebClientResponseException.create(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                HttpHeaders.EMPTY,
                "{\"error\":\"invalid request\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );

        var diagnostics = service.extractBadRequestDiagnostics(error);

        assertTrue(diagnostics.isPresent());
        assertEquals(400, diagnostics.get().statusCode());
        assertEquals("{\"error\":\"invalid request\"}", diagnostics.get().payload());
    }

    @Test
    void redactsSensitiveTokensFromErrorPayload() {
        HistoricalDailyService service = createService(new CapturingEventPublisher());

        String redacted = service.redactAndLimitPayload(
                "{\"access_token\":\"abc123\",\"refresh_token\":\"def456\",\"message\":\"bad request\"}"
        );

        assertTrue(redacted.contains("access_token=<redacted>"));
        assertTrue(redacted.contains("refresh_token=<redacted>"));
    }

    private HistoricalDailyService createService(ApplicationEventPublisher eventPublisher) {
        StubSchwabSessionService sessionService = new StubSchwabSessionService();
        AuthBrowserLauncher authBrowserLauncher = new AuthBrowserLauncher(sessionService);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-03T12:00:00Z"), ZoneId.of("America/New_York"));
        return new HistoricalDailyService(sessionService, authBrowserLauncher, eventPublisher, fixedClock);
    }

    private static Candle candle(LocalDateTime dateTime, int open, int high, int low, int close, long volume) {
        Candle candle = new Candle();
        candle.setDatetimeISO8601(dateTime);
        candle.setOpen(BigDecimal.valueOf(open));
        candle.setHigh(BigDecimal.valueOf(high));
        candle.setLow(BigDecimal.valueOf(low));
        candle.setClose(BigDecimal.valueOf(close));
        candle.setVolume(volume);
        return candle;
    }

    private static final class CapturingEventPublisher implements ApplicationEventPublisher {
        private final List<Object> events = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            events.add(event);
        }

        @Override
        public void publishEvent(ApplicationEvent event) {
            events.add(event);
        }

        private List<Object> events() {
            return events;
        }
    }

    private static final class StubSchwabSessionService extends SchwabSessionService {
        private StubSchwabSessionService() {
            super(null, null, null, null);
        }

        @Override
        public boolean hasValidRefreshToken() {
            return true;
        }

        @Override
        public SchwabMarketDataApiClient client() {
            throw new UnsupportedOperationException("Not needed for unit tests.");
        }

        @Override
        public String authStartUrl() {
            return "http://localhost/oauth2/start";
        }
    }
}
