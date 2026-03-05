package net.uberfoo.jquote.jquote.quote;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record IntradayPoint(LocalDateTime timestamp,
                           BigDecimal open,
                           BigDecimal high,
                           BigDecimal low,
                           BigDecimal close,
                           Long volume) {
}
