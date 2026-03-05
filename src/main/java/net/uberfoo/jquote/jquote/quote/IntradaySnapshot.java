package net.uberfoo.jquote.jquote.quote;

import java.util.List;

public record IntradaySnapshot(String symbol,
                               IntradaySession session,
                               boolean extendedHoursSupported,
                               List<IntradayPoint> points) {
}
