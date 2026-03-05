package net.uberfoo.jquote.jquote.quote;

import java.time.LocalDate;

public enum HistoricalRangePreset {
    DAYS_30(30),
    DAYS_90(90),
    DAYS_120(120);

    private final int days;

    HistoricalRangePreset(int days) {
        this.days = days;
    }

    public HistoricalDateRange toDateRange(LocalDate endDate) {
        LocalDate startDate = endDate.minusDays(days - 1L);
        return new HistoricalDateRange(startDate, endDate);
    }
}
