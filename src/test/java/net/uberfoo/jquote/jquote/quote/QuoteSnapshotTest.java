package net.uberfoo.jquote.jquote.quote;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuoteSnapshotTest {

    @Test
    void priceStateUsesDailyChangeWhenPresent() {
        QuoteSnapshot upSnapshot = snapshot(
                new BigDecimal("1.25"),
                new BigDecimal("-2.10"),
                new BigDecimal("103.00"),
                new BigDecimal("100.00"));
        QuoteSnapshot downSnapshot = snapshot(
                new BigDecimal("-0.80"),
                new BigDecimal("1.40"),
                new BigDecimal("95.00"),
                new BigDecimal("100.00"));

        assertEquals(QuoteSnapshot.PriceState.UP, upSnapshot.priceState().orElseThrow());
        assertEquals(QuoteSnapshot.PriceState.DOWN, downSnapshot.priceState().orElseThrow());
    }

    @Test
    void priceStateFallsBackToPercentChangeWhenNetChangeMissing() {
        QuoteSnapshot upSnapshot = snapshot(
                null,
                new BigDecimal("0.55"),
                new BigDecimal("99.00"),
                new BigDecimal("101.00"));
        QuoteSnapshot downSnapshot = snapshot(
                null,
                new BigDecimal("-0.15"),
                new BigDecimal("101.00"),
                new BigDecimal("99.00"));

        assertEquals(QuoteSnapshot.PriceState.UP, upSnapshot.priceState().orElseThrow());
        assertEquals(QuoteSnapshot.PriceState.DOWN, downSnapshot.priceState().orElseThrow());
    }

    @Test
    void priceStateFallsBackToLastVsCloseWhenChangeUnavailable() {
        QuoteSnapshot upSnapshot = snapshot(
                null,
                null,
                new BigDecimal("101.00"),
                new BigDecimal("100.00"));
        QuoteSnapshot downSnapshot = snapshot(
                null,
                null,
                new BigDecimal("99.00"),
                new BigDecimal("100.00"));

        assertEquals(QuoteSnapshot.PriceState.UP, upSnapshot.priceState().orElseThrow());
        assertEquals(QuoteSnapshot.PriceState.DOWN, downSnapshot.priceState().orElseThrow());
    }

    @Test
    void priceStateIsEmptyWhenNoDailyComparisonData() {
        QuoteSnapshot snapshot = new QuoteSnapshot(
                "AAPL",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false
        );

        assertTrue(snapshot.priceState().isEmpty());
    }

    private QuoteSnapshot snapshot(BigDecimal change,
                                   BigDecimal percentChange,
                                   BigDecimal lastPrice,
                                   BigDecimal closePrice) {
        return new QuoteSnapshot(
                "AAPL",
                null,
                lastPrice,
                change,
                percentChange,
                new BigDecimal("98.00"),
                closePrice,
                lastPrice,
                true
        );
    }
}
