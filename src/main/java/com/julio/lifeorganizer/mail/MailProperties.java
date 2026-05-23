package com.julio.lifeorganizer.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configures the {@link MailService} backend (Slice 11).
 *
 * <p>{@code provider}:
 * <ul>
 *   <li>{@code file} (default) - links are written to the dev-delivery file via
 *       {@link FileMailService}. Suitable for local development and single-user
 *       self-host scenarios where the operator reads links from the filesystem.</li>
 *   <li>{@code smtp} - links are emailed via {@link SmtpMailService} using
 *       Spring's {@code JavaMailSender}. Works with any SMTP-compliant server
 *       configured through the standard {@code spring.mail.*} properties.</li>
 * </ul>
 *
 * <p>{@code fromAddress} and {@code fromName} are required when {@code provider=smtp};
 * the SMTP service rejects boot if {@code fromAddress} is blank.
 *
 * <p>{@code baseUrl} is the public origin of the SPA (e.g. {@code https://my.app}).
 * The auth services pass relative paths like {@code /reset-password?token=...} and the
 * SMTP service prepends {@code baseUrl} so the link in the email is clickable.
 * Defaults to {@code http://localhost:4200} for local development.
 */
@ConfigurationProperties(prefix = "app.mail")
public class MailProperties {

    private String provider = "file";
    private String fromAddress = "";
    private String fromName = "Life Organizer";
    private String baseUrl = "http://localhost:4200";

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }

    public String getFromName() { return fromName; }
    public void setFromName(String fromName) { this.fromName = fromName; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
}
