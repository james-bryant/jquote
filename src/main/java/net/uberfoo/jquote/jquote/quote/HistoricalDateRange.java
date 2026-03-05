package net.uberfoo.jquote.jquote.quote;

import java.time.LocalDate;
import java.util.Optional;

public record HistoricalDateRange(LocalDate startDate, LocalDate endDate) {
    public static Optional<String> validate(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            return Optional.of("Start date and end date are required.");
        }
        if (startDate.isAfter(endDate)) {
            return Optional.of("Start date must be on or before end date.");
        }
        return Optional.empty();
    }
}
