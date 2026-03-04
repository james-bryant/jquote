package net.uberfoo.jquote.jquote.schwab;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangility.schwab.api.client.oauth2.SchwabAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class SchwabTokenStore {
    private static final Logger log = LoggerFactory.getLogger(SchwabTokenStore.class);

    private final ObjectMapper objectMapper;
    private final Path tokenPath;

    public SchwabTokenStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.tokenPath = Path.of(System.getProperty("user.home"), ".jquote", "schwab-token.json");
    }

    public Optional<SchwabAccount> load() {
        if (!Files.exists(tokenPath)) {
            return Optional.empty();
        }
        try {
            TokenState state = objectMapper.readValue(Files.readString(tokenPath), TokenState.class);
            return Optional.of(state.toAccount());
        } catch (IOException e) {
            log.warn("Failed to read Schwab token file at {}", tokenPath, e);
            return Optional.empty();
        }
    }

    public synchronized void save(SchwabAccount account) {
        if (account == null) {
            return;
        }
        TokenState state = TokenState.from(account);
        try {
            Files.createDirectories(tokenPath.getParent());
            Path tempFile = Files.createTempFile(tokenPath.getParent(), "schwab-token", ".json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), state);
            try {
                Files.move(tempFile, tokenPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tempFile, tokenPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.warn("Failed to write Schwab token file at {}", tokenPath, e);
        }
    }

    private record TokenState(
            String userId,
            String refreshToken,
            String accessToken,
            LocalDateTime refreshExpiration,
            LocalDateTime accessExpiration
    ) {
        private static TokenState from(SchwabAccount account) {
            return new TokenState(
                    account.getUserId(),
                    account.getRefreshToken(),
                    account.getAccessToken(),
                    account.getRefreshExpiration(),
                    account.getAccessExpiration()
            );
        }

        private SchwabAccount toAccount() {
            SchwabAccount account = new SchwabAccount();
            account.setUserId(userId);
            account.setRefreshToken(refreshToken);
            account.setAccessToken(accessToken);
            account.setRefreshExpiration(refreshExpiration);
            account.setAccessExpiration(accessExpiration);
            return account;
        }
    }
}
