package com.julio.lifeorganizer.mail;

/**
 * Outbound delivery for the four auth flows that hand the user a magic link
 * (Slices 8 + 9). Implementations:
 * <ul>
 *   <li>{@link FileMailService} - default; writes to a local file</li>
 *   <li>{@link SmtpMailService} - real email via Spring's JavaMailSender</li>
 * </ul>
 *
 * <p>Each method takes the recipient's email and display name plus the relative
 * link path (e.g. {@code /reset-password?token=...}). The SMTP implementation
 * prepends {@link MailProperties#getBaseUrl()} when rendering the absolute URL.
 *
 * <p>Methods MUST swallow delivery failures and log a WARN. The calling endpoint
 * still returns its documented success response. This preserves the anti-
 * enumeration guarantees from Slice 8 (a 200 means "we tried", not "we delivered").
 */
public interface MailService {

    void sendPasswordReset(String toEmail, String displayName, String linkPath);

    void sendEmailVerification(String toEmail, String displayName, String linkPath);

    void sendEmailChangeConfirmation(String toEmail, String displayName, String linkPath);

    void sendAccountRestore(String toEmail, String displayName, String linkPath);
}
