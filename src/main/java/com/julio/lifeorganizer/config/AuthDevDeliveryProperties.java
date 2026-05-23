package com.julio.lifeorganizer.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configures how password-reset and email-verification links are surfaced to the
 * self-hosted operator while SMTP delivery is not yet wired up (Slice 8 decision 6).
 *
 * <p>Design rationale: tokens are credentials. Writing them to the standard application
 * logger means anyone with log access (docker logs, log aggregators, anyone reading
 * stdout over someone's shoulder) can hijack accounts during the token TTL. Instead,
 * this component writes the links to a single, opt-in local file (default
 * {@code .tmp/auth-dev-links.txt}). The application logger only records that an event
 * happened, never the token itself.
 *
 * <p>To enable for self-hosted use, set {@code app.auth.dev-delivery.enabled=true} in
 * the active profile (typically {@code application-local.yml} or a {@code .env}). For
 * production deployments with real email delivery, leave it disabled (the default) -
 * the file is never written and tokens stay only in memory between issuance and the
 * outbound email.
 */
@ConfigurationProperties(prefix = "app.auth.dev-delivery")
public class AuthDevDeliveryProperties {

    private static final Logger log = LoggerFactory.getLogger(AuthDevDeliveryProperties.class);

    private boolean enabled = false;
    private String filePath = ".tmp/auth-dev-links.txt";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    /**
     * Appends a single delivery record to the configured file. No-op when disabled.
     * The application logger sees only short, non-sensitive metadata lines - never
     * the link itself.
     */
    public void write(String kind, long userId, String email, String linkPath) {
        if (!enabled) {
            return;
        }
        String line = String.format(
                "[%s] kind=%s userId=%d email=%s link=%s%n",
                Instant.now(), kind, userId, email, linkPath);
        Path target = Path.of(filePath);
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(target, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            // Stay silent about the path in logs to avoid leaking deployment layout.
            log.warn("auth dev-delivery: write failed (check app.auth.dev-delivery.file-path)");
        }
    }
}
