package net.uberfoo.jquote.jquote.schwab;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AuthBrowserLauncher {
    private static final Logger log = LoggerFactory.getLogger(AuthBrowserLauncher.class);
    private static final long LAUNCH_COOLDOWN_MS = Duration.ofMinutes(5).toMillis();

    private final SchwabSessionService sessionService;
    private final AtomicLong lastLaunchEpochMs = new AtomicLong(0);

    public AuthBrowserLauncher(SchwabSessionService sessionService) {
        this.sessionService = sessionService;
    }

    public void launchIfNeeded() {
        if (!sessionService.hasValidRefreshToken()) {
            launch();
        }
    }

    public void launch() {
        long now = System.currentTimeMillis();
        long last = lastLaunchEpochMs.get();
        if (now - last < LAUNCH_COOLDOWN_MS) {
            return;
        }
        if (!lastLaunchEpochMs.compareAndSet(last, now)) {
            return;
        }
        String url = sessionService.authStartUrl();
        open(url);
    }

    private void open(String url) {
        try {
            if (!Desktop.isDesktopSupported()) {
                log.warn("Desktop browse is not supported. Open URL manually: {}", url);
                return;
            }
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.BROWSE)) {
                log.warn("Desktop browse action is not supported. Open URL manually: {}", url);
                return;
            }
            desktop.browse(URI.create(url));
            log.info("Opened browser for Schwab authorization");
        } catch (Exception e) {
            log.warn("Failed to open browser for Schwab authorization. Open URL manually: {}", url, e);
        }
    }
}
