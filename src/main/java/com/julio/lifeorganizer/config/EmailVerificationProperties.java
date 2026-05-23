package com.julio.lifeorganizer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Toggles the "verify your email" friction step (Slice 8) at boot.
 *
 * <p>Default is OFF: this app is built for single-operator self-host first.
 * When disabled, registration auto-marks new users as verified and the
 * verification email is not sent. The /verify-email endpoint and the
 * "verify your email" dashboard banner keep working defensively - if any
 * link is in flight or a downgrade-then-upgrade happens, the existing
 * machinery still handles it.
 *
 * <p>To enable (when sharing the app with other users), set
 * {@code app.auth.email-verification.enabled=true} or the env var
 * {@code APP_AUTH_EMAIL_VERIFICATION_ENABLED=true}.
 *
 * <p>This flag is independent of {@code app.mail.provider} - turning
 * verification back on without SMTP configured will silently drop the
 * verification email (see {@link com.julio.lifeorganizer.mail.FileMailService}
 * for the startup warning that surfaces that config gap).
 */
@ConfigurationProperties(prefix = "app.auth.email-verification")
public class EmailVerificationProperties {

    private boolean enabled = false;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
