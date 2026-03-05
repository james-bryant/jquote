package net.uberfoo.jquote.jquote.quote;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyOhlcPoint(
        LocalDate date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        Long volume
) {
}
