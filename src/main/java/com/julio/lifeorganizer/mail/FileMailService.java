package com.julio.lifeorganizer.mail;

import com.julio.lifeorganizer.config.AuthDevDeliveryProperties;
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

    private final AuthDevDeliveryProperties devDelivery;

    public FileMailService(AuthDevDeliveryProperties devDelivery) {
        this.devDelivery = devDelivery;
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
