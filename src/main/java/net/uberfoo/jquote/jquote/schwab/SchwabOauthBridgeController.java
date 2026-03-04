package net.uberfoo.jquote.jquote.schwab;

import com.pangility.schwab.api.client.oauth2.SchwabOauth2Controller;
import net.uberfoo.jquote.jquote.config.JQuoteSchwabProperties;
import net.uberfoo.jquote.jquote.ui.UiFocusService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Controller
public class SchwabOauthBridgeController {
    private final SchwabOauth2Controller schwabOauth2Controller;
    private final SchwabSessionService sessionService;
    private final JQuoteSchwabProperties properties;
    private final UiFocusService uiFocusService;

    public SchwabOauthBridgeController(SchwabOauth2Controller schwabOauth2Controller,
                                       SchwabSessionService sessionService,
                                       JQuoteSchwabProperties properties,
                                       UiFocusService uiFocusService) {
        this.schwabOauth2Controller = schwabOauth2Controller;
        this.sessionService = sessionService;
        this.properties = properties;
        this.uiFocusService = uiFocusService;
    }

    @GetMapping("/callback")
    public Mono<RedirectView> callback(@RequestParam("code") String code, @RequestParam("state") String state) {
        uiFocusService.requestFocus();
        return schwabOauth2Controller.processCode(code, state);
    }

    @GetMapping("/oauth2/start")
    public RedirectView start() {
        String target = UriComponentsBuilder.fromHttpUrl(properties.baseUrl())
                .path("/oauth2/schwab/authorization")
                .queryParam("schwabUserId", sessionService.userId())
                .queryParam("callback", properties.postAuthRedirect())
                .build()
                .toUriString();
        return new RedirectView(target);
    }

    @GetMapping("/oauth2/authorized")
    @ResponseBody
    public String authorized() {
        uiFocusService.requestFocus();
        return "Schwab authorization completed. You can return to the app.";
    }
}
