package net.uberfoo.jquote.jquote.schwab;

import com.pangility.schwab.api.client.marketdata.SchwabMarketDataApiClient;
import com.pangility.schwab.api.client.oauth2.SchwabAccount;
import net.uberfoo.jquote.jquote.config.JQuoteSchwabProperties;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class SchwabSessionService {
    private final SchwabMarketDataApiClient marketDataApiClient;
    private final SchwabTokenStore tokenStore;
    private final JQuoteTokenHandler tokenHandler;
    private final JQuoteSchwabProperties properties;
    private final AtomicReference<SchwabAccount> accountRef = new AtomicReference<>();

    public SchwabSessionService(SchwabMarketDataApiClient marketDataApiClient,
                                SchwabTokenStore tokenStore,
                                JQuoteTokenHandler tokenHandler,
                                JQuoteSchwabProperties properties) {
        this.marketDataApiClient = marketDataApiClient;
        this.tokenStore = tokenStore;
        this.tokenHandler = tokenHandler;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        SchwabAccount account = tokenStore.load().orElseGet(SchwabAccount::new);
        if (account.getUserId() == null || account.getUserId().isBlank()) {
            account.setUserId(properties.userId());
        }
        if (account.getRefreshToken() != null && account.getRefreshExpiration() == null) {
            account.setRefreshExpiration(LocalDateTime.now().minusDays(1));
        }
        marketDataApiClient.init(account.getUserId(), List.of(account), tokenHandler);
        accountRef.set(account);
    }

    public SchwabMarketDataApiClient client() {
        return marketDataApiClient;
    }

    public String userId() {
        SchwabAccount account = accountRef.get();
        return account == null ? properties.userId() : account.getUserId();
    }

    public boolean hasValidRefreshToken() {
        SchwabAccount account = accountRef.get();
        if (account == null) {
            return false;
        }
        if (account.getRefreshToken() == null || account.getRefreshToken().isBlank()) {
            return false;
        }
        LocalDateTime expiration = account.getRefreshExpiration();
        if (expiration == null) {
            return false;
        }
        return LocalDateTime.now().plusMinutes(60).isBefore(expiration);
    }

    public String authStartUrl() {
        return properties.baseUrl() + "/oauth2/start";
    }

    public String postAuthRedirect() {
        return properties.postAuthRedirect();
    }
}
