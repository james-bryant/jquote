package net.uberfoo.jquote.jquote.quote;

public record HistoricalDailyErrorEvent(String symbol, HistoricalDateRange range, String message) {
}
