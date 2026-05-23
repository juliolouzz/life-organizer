package com.julio.lifeorganizer.mail;

import com.julio.lifeorganizer.config.AuthDevDeliveryProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Default MailService impl. Forwards each link to the existing
 * {@link AuthDevDeliveryProperties} sink (a local file, opt-in via
 * {@code app.auth.dev-delivery.enabled}). Suitable for development and
 * single-user self-host where reading links from the filesystem is fine.
 *
 * <p>Active when {@code app.mail.provider} is unset or {@code file}.
 */
@Service
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "file", matchIfMissing = true)
public class FileMailService implements MailService {

    private static final Logger log = LoggerFactory.getLogger(FileMailService.class);

    private final AuthDevDeliveryProperties devDelivery;

    public FileMailService(AuthDevDeliveryProperties devDelivery) {
        this.devDelivery = devDelivery;
    }

    /**
     * Slice 13: surface the silent-drop configuration at boot. When the file
     * delivery sink is disabled (the default) AND app.mail.provider is "file",
     * every magic link the app issues (verify, reset, change-email, restore)
     * goes nowhere. Make that visible so operators can fix it instead of
     * wondering why their email never arrives.
     */
    @PostConstruct
    void warnIfDeliveryDisabled() {
        if (!devDelivery.isEnabled()) {
            log.warn("MAIL CONFIG: app.mail.provider=file but "
                    + "app.auth.dev-delivery.enabled=false. "
                    + "Verification / reset / change-email / restore links will be "
                    + "silently dropped. Set APP_AUTH_DEV_DELIVERY_ENABLED=true to "
                    + "write them to {} (read it with `docker exec ... cat <file>`), "
                    + "OR set APP_MAIL_PROVIDER=smtp + the standard SPRING_MAIL_* "
                    + "properties to send real emails. See docs/deployment.md.",
                    devDelivery.getFilePath());
        } else {
            log.info("MAIL CONFIG: file delivery enabled - magic links will be appended to {}",
                    devDelivery.getFilePath());
        }
    }

    @Override
    public void sendPasswordReset(String toEmail, String displayName, String linkPath) {
        devDelivery.write("reset-password", 0L, toEmail, linkPath);
    }

    @Override
    public void sendEmailVerification(String toEmail, String displayName, String linkPath) {
        devDelivery.write("verify-email", 0L, toEmail, linkPath);
    }

    @Override
    public void sendEmailChangeConfirmation(String toEmail, String displayName, String linkPath) {
        devDelivery.write("change-email", 0L, toEmail, linkPath);
    }

    @Override
    public void sendAccountRestore(String toEmail, String displayName, String linkPath) {
        devDelivery.write("restore-account", 0L, toEmail, linkPath);
    }
}
