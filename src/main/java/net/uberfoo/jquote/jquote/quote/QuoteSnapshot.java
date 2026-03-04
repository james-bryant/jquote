package net.uberfoo.jquote.jquote.quote;

import com.pangility.schwab.api.client.marketdata.model.AssetMainType;

import java.math.BigDecimal;

public record QuoteSnapshot(
        String symbol,
        AssetMainType assetType,
        BigDecimal lastPrice,
        BigDecimal change,
        BigDecimal percentChange
) {
    public String format() {
        StringBuilder builder = new StringBuilder();
        builder.append(symbol);
        if (assetType != null) {
            builder.append(" ").append(assetType);
        }
        if (lastPrice != null) {
            builder.append(" ").append(formatNumber(lastPrice));
        }
        if (change != null || percentChange != null) {
            builder.append(" (");
            if (change != null) {
                builder.append(formatSigned(change));
            }
            if (percentChange != null) {
                if (change != null) {
                    builder.append(" / ");
                }
                builder.append(formatSigned(percentChange)).append("%");
            }
            builder.append(")");
        }
        return builder.toString();
    }

    private static String formatNumber(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String formatSigned(BigDecimal value) {
        String formatted = formatNumber(value);
        if (!formatted.startsWith("-") && !formatted.startsWith("+")) {
            return "+" + formatted;
        }
        return formatted;
    }
}
