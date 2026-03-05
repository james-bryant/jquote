package net.uberfoo.jquote.jquote.quote;

import java.util.List;

public record HistoricalDailySnapshot(String symbol, HistoricalDateRange range, List<DailyOhlcPoint> points) {
}
