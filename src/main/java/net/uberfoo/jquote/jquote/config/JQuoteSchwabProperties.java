package net.uberfoo.jquote.jquote.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jquote.schwab")
public record JQuoteSchwabProperties(
        String userId,
        String baseUrl,
        String postAuthRedirect
) {
}
