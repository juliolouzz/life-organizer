package com.julio.lifeorganizer.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Real email delivery via Spring's JavaMailSender. Bound to any SMTP server
 * via the standard {@code spring.mail.*} properties.
 *
 * <p>Failures NEVER propagate to the caller. {@link MailException} is logged at
 * WARN with the recipient and template name; the calling endpoint's
 * anti-enumeration response is preserved.
 *
 * <p>Active when {@code app.mail.provider=smtp}.
 */
@Service
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "smtp")
public class SmtpMailService implements MailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final MailProperties props;

    public SmtpMailService(JavaMailSender mailSender,
                           TemplateEngine templateEngine,
                           MailProperties props) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.props = props;
        if (props.getFromAddress() == null || props.getFromAddress().isBlank()) {
            throw new IllegalStateException(
                    "app.mail.from-address must be set when app.mail.provider=smtp");
        }
    }

    @Override
    public void sendPasswordReset(String to, String displayName, String linkPath) {
        send(to, displayName, linkPath,
                "Reset your Life Organizer password",
                "mail/password-reset");
    }

    @Override
    public void sendEmailVerification(String to, String displayName, String linkPath) {
        send(to, displayName, linkPath,
                "Verify your Life Organizer email",
                "mail/verify-email");
    }

    @Override
    public void sendEmailChangeConfirmation(String to, String displayName, String linkPath) {
        send(to, displayName, linkPath,
                "Confirm your new email address",
                "mail/change-email");
    }

    @Override
    public void sendAccountRestore(String to, String displayName, String linkPath) {
        send(to, displayName, linkPath,
                "Restore your Life Organizer account",
                "mail/account-restore");
    }

    private void send(String to, String displayName, String linkPath,
                      String subject, String template) {
        String fullUrl = buildAbsoluteUrl(linkPath);
        String html = render(template, displayName, fullUrl);
        String text = htmlToPlainText(html, fullUrl);

        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mime, true, StandardCharsets.UTF_8.name());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setFrom(new InternetAddress(
                    props.getFromAddress(),
                    props.getFromName(),
                    StandardCharsets.UTF_8.name()));
            helper.setReplyTo(props.getFromAddress());
            // setText(text, html) attaches BOTH alternatives so spam filters and
            // text-only clients (e.g. some mailing lists) get a usable rendering.
            helper.setText(text, html);
            mailSender.send(mime);
            log.info("mail sent: template={} to={}", template, to);
        } catch (MailException | MessagingException | UnsupportedEncodingException ex) {
            // Best-effort: callers expect anti-enumeration success regardless.
            log.warn("mail delivery failed: template={} to={} error={}",
                    template, to, ex.getMessage());
        }
    }

    private String render(String template, String displayName, String fullUrl) {
        Context ctx = new Context(Locale.ROOT);
        ctx.setVariable("displayName", displayName);
        ctx.setVariable("link", fullUrl);
        return templateEngine.process(template, ctx);
    }

    private String buildAbsoluteUrl(String linkPath) {
        String base = props.getBaseUrl();
        if (base == null || base.isBlank()) return linkPath;
        if (base.endsWith("/") && linkPath.startsWith("/")) {
            return base.substring(0, base.length() - 1) + linkPath;
        }
        if (!base.endsWith("/") && !linkPath.startsWith("/")) {
            return base + "/" + linkPath;
        }
        return base + linkPath;
    }

    /**
     * Strip HTML tags + collapse whitespace for the plain-text alternative. We
     * also append the raw URL on its own line so plain-text clients always see
     * a clickable link.
     */
    private static String htmlToPlainText(String html, String url) {
        String stripped = html
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
        return stripped + System.lineSeparator() + System.lineSeparator()
                + "Direct link: " + url + System.lineSeparator();
    }
}
