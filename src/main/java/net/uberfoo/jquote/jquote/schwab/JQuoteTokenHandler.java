package net.uberfoo.jquote.jquote.schwab;

import com.pangility.schwab.api.client.oauth2.SchwabAccount;
import com.pangility.schwab.api.client.oauth2.SchwabTokenHandler;
import org.springframework.stereotype.Component;

@Component
public class JQuoteTokenHandler implements SchwabTokenHandler {
    private final SchwabTokenStore tokenStore;

    public JQuoteTokenHandler(SchwabTokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @Override
    public void onAccessTokenChange(SchwabAccount schwabAccount) {
        tokenStore.save(schwabAccount);
    }

    @Override
    public void onRefreshTokenChange(SchwabAccount schwabAccount) {
        tokenStore.save(schwabAccount);
    }
}
