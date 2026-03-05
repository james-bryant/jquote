package net.uberfoo.jquote.jquote.quote;

import com.pangility.schwab.api.client.marketdata.SchwabMarketDataApiClient;
import com.pangility.schwab.api.client.marketdata.model.pricehistory.Candle;
import com.pangility.schwab.api.client.marketdata.model.pricehistory.FrequencyType;
import com.pangility.schwab.api.client.marketdata.model.pricehistory.PeriodType;
import com.pangility.schwab.api.client.marketdata.model.pricehistory.PriceHistoryRequest;
import com.pangility.schwab.api.client.marketdata.model.pricehistory.PriceHistoryResponse;
import net.uberfoo.jquote.jquote.schwab.AuthBrowserLauncher;
import net.uberfoo.jquote.jquote.schwab.SchwabSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntradayServiceTest {
    @Test
    void buildsFiveMinuteRequestForSingleTradingDay() {
        IntradayService service = createService(new CapturingEventPublisher(), new ControlledMarketDataClient());

        PriceHistoryRequest request = service.buildRequest("MSFT", IntradaySession.EXTENDED);

        assertEquals("MSFT", request.getSymbol());
        assertEquals(PeriodType.day, request.getPeriodType());
        assertEquals(1, request.getPeriod());
        assertEquals(FrequencyType.minute, request.getFrequencyType());
        assertEquals(5, request.getFrequency());
        assertEquals(request.getStartDate(), request.getEndDate());
        assertTrue(request.getNeedExtendedHoursData());
    }

    @Test
    void mapsResponseToSortedIntradayOhlcAndDetectsExtendedHoursSupport() {
        IntradayService service = createService(new CapturingEventPublisher(), new ControlledMarketDataClient());
        Candle regular = candle(LocalDateTime.of(2026, 3, 3, 10, 15), 101, 102, 100, 101, 1200);
        Candle extended = candle(LocalDateTime.of(2026, 3, 3, 8, 45), 99, 100, 98, 99, 900);
        Candle incomplete = candle(LocalDateTime.of(2026, 3, 3, 9, 45), 100, 101, 99, 100, 1000);
        incomplete.setLow(null);
        PriceHistoryResponse response = new PriceHistoryResponse();
        response.setCandles(List.of(regular, incomplete, extended));

        IntradaySnapshot snapshot = service.toSnapshot(response, "AAPL", IntradaySession.EXTENDED);

        assertEquals("AAPL", snapshot.symbol());
        assertEquals(IntradaySession.EXTENDED, snapshot.session());
        assertTrue(snapshot.extendedHoursSupported());
        assertEquals(2, snapshot.points().size());
        assertEquals(LocalDateTime.of(2026, 3, 3, 8, 45), snapshot.points().get(0).timestamp());
        assertEquals(new BigDecimal("99"), snapshot.points().get(0).open());
        assertEquals(new BigDecimal("101"), snapshot.points().get(1).close());
    }

    @Test
    void handlesExtendedSessionResponseWithoutExtendedData() {
        IntradayService service = createService(new CapturingEventPublisher(), new ControlledMarketDataClient());
        Candle regular = candle(LocalDateTime.of(2026, 3, 3, 10, 15), 101, 102, 100, 101, 1200);
        PriceHistoryResponse response = new PriceHistoryResponse();
        response.setEmpty(Boolean.FALSE);
        response.setCandles(List.of(regular));

        IntradaySnapshot snapshot = service.toSnapshot(response, "AAPL", IntradaySession.EXTENDED);

        assertFalse(snapshot.extendedHoursSupported());
        assertEquals(1, snapshot.points().size());
    }

    @Test
    void handlesEmptyExtendedSessionResponseWithoutError() {
        IntradayService service = createService(new CapturingEventPublisher(), new ControlledMarketDataClient());
        PriceHistoryResponse response = new PriceHistoryResponse();
        response.setEmpty(Boolean.TRUE);
        response.setCandles(List.of());

        IntradaySnapshot snapshot = service.toSnapshot(response, "AAPL", IntradaySession.EXTENDED);

        assertNotNull(snapshot);
        assertFalse(snapshot.extendedHoursSupported());
        assertTrue(snapshot.points().isEmpty());
    }

    @Test
    void ignoresStaleIntradayResponsesWhenSymbolChanges() {
        ControlledMarketDataClient client = new ControlledMarketDataClient();
        Sinks.One<PriceHistoryResponse> firstResponse = client.queueResponse();
        Sinks.One<PriceHistoryResponse> secondResponse = client.queueResponse();
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        IntradayService service = createService(eventPublisher, client);

        service.requestIntraday("AAPL", IntradaySession.REGULAR);
        service.requestIntraday("MSFT", IntradaySession.REGULAR);

        firstResponse.tryEmitValue(priceHistory(candle(LocalDateTime.of(2026, 3, 3, 10, 0), 100, 101, 99, 100, 1000)));
        secondResponse.tryEmitValue(priceHistory(candle(LocalDateTime.of(2026, 3, 3, 10, 1), 200, 201, 199, 200, 1500)));

        List<IntradayUpdateEvent> updates = eventPublisher.events().stream()
                .filter(IntradayUpdateEvent.class::isInstance)
                .map(IntradayUpdateEvent.class::cast)
                .toList();
        assertEquals(1, updates.size());
        assertEquals("MSFT", updates.getFirst().snapshot().symbol());
    }

    private IntradayService createService(ApplicationEventPublisher eventPublisher, SchwabMarketDataApiClient client) {
        StubSchwabSessionService sessionService = new StubSchwabSessionService(client);
        AuthBrowserLauncher authBrowserLauncher = new AuthBrowserLauncher(sessionService);
        return new IntradayService(sessionService, authBrowserLauncher, eventPublisher);
    }

    private static Candle candle(LocalDateTime timestamp, int open, int high, int low, int close, long volume) {
        Candle candle = new Candle();
        candle.setDatetimeISO8601(timestamp);
        candle.setOpen(BigDecimal.valueOf(open));
        candle.setHigh(BigDecimal.valueOf(high));
        candle.setLow(BigDecimal.valueOf(low));
        candle.setClose(BigDecimal.valueOf(close));
        candle.setVolume(volume);
        return candle;
    }

    private static PriceHistoryResponse priceHistory(Candle candle) {
        PriceHistoryResponse response = new PriceHistoryResponse();
        response.setCandles(List.of(candle));
        response.setEmpty(Boolean.FALSE);
        return response;
    }

    private static final class ControlledMarketDataClient extends SchwabMarketDataApiClient {
        private final List<Sinks.One<PriceHistoryResponse>> queuedResponses = new ArrayList<>();
        private int nextResponseIndex = 0;

        private Sinks.One<PriceHistoryResponse> queueResponse() {
            Sinks.One<PriceHistoryResponse> sink = Sinks.one();
            queuedResponses.add(sink);
            return sink;
        }

        @Override
        public Mono<PriceHistoryResponse> fetchPriceHistoryToMono(PriceHistoryRequest request) {
            if (nextResponseIndex >= queuedResponses.size()) {
                return Mono.error(new IllegalStateException("No queued intraday response for " + request.getSymbol()));
            }
            Sinks.One<PriceHistoryResponse> sink = queuedResponses.get(nextResponseIndex);
            nextResponseIndex++;
            return sink.asMono();
        }
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
        private final SchwabMarketDataApiClient client;

        private StubSchwabSessionService(SchwabMarketDataApiClient client) {
            super(null, null, null, null);
            this.client = client;
        }

        @Override
        public boolean hasValidRefreshToken() {
            return true;
        }

        @Override
        public SchwabMarketDataApiClient client() {
            return client;
        }

        @Override
        public String authStartUrl() {
            return "http://localhost/oauth2/start";
        }
    }
}
