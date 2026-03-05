package net.uberfoo.jquote.jquote.quote;

public record IntradayErrorEvent(String symbol, IntradaySession session, String message) {
}
