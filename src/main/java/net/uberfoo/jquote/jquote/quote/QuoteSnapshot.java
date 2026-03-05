package net.uberfoo.jquote.jquote.quote;

import com.pangility.schwab.api.client.marketdata.model.AssetMainType;

import java.math.BigDecimal;
import java.util.Optional;

public record QuoteSnapshot(
        String symbol,
        AssetMainType assetType,
        BigDecimal lastPrice,
        BigDecimal change,
        BigDecimal percentChange,
        BigDecimal openPrice,
        BigDecimal closePrice,
        BigDecimal latestTickPrice,
        boolean marketOpen
) {
    public enum PriceState {
        UP,
        DOWN
    }

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

    public Optional<PriceState> priceState() {
        if (change != null) {
            int changeResult = change.compareTo(BigDecimal.ZERO);
            if (changeResult > 0) {
                return Optional.of(PriceState.UP);
            }
            if (changeResult < 0) {
                return Optional.of(PriceState.DOWN);
            }
            return Optional.empty();
        }
        if (percentChange != null) {
            int percentResult = percentChange.compareTo(BigDecimal.ZERO);
            if (percentResult > 0) {
                return Optional.of(PriceState.UP);
            }
            if (percentResult < 0) {
                return Optional.of(PriceState.DOWN);
            }
            return Optional.empty();
        }
        if (lastPrice != null && closePrice != null) {
            int closeComparison = lastPrice.compareTo(closePrice);
            if (closeComparison > 0) {
                return Optional.of(PriceState.UP);
            }
            if (closeComparison < 0) {
                return Optional.of(PriceState.DOWN);
            }
            return Optional.empty();
        }
        if (openPrice == null) {
            return Optional.empty();
        }
        BigDecimal comparisonPrice = marketOpen ? latestTickPrice : closePrice;
        if (comparisonPrice == null) {
            return Optional.empty();
        }
        int openComparison = comparisonPrice.compareTo(openPrice);
        if (openComparison > 0) {
            return Optional.of(PriceState.UP);
        }
        if (openComparison < 0) {
            return Optional.of(PriceState.DOWN);
        }
        return Optional.empty();
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
