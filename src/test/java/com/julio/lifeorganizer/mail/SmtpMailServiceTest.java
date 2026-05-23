package com.julio.lifeorganizer.mail;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class SmtpMailServiceTest {

    @RegisterExtension
    static final GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    @Test
    void sendPasswordReset_deliversHtmlAndPlainTextWithAbsoluteLink() throws Exception {
        SmtpMailService service = newService("https://my.app");

        service.sendPasswordReset("alice@example.com", "Alice",
                "/reset-password?token=abc123");

        MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);
        MimeMessage msg = received[0];
        assertThat(msg.getSubject()).isEqualTo("Reset your Life Organizer password");
        assertThat(msg.getAllRecipients()[0].toString()).isEqualTo("alice@example.com");
        assertThat(msg.getFrom()[0].toString()).contains("no-reply@example.com");
        assertThat(msg.getContent()).isInstanceOf(MimeMultipart.class);

        String body = GreenMailUtil.getBody(msg);
        assertThat(body).contains("https://my.app/reset-password?token=abc123");
        assertThat(body).contains("Alice");
    }

    @Test
    void sendEmailVerification_subjectAndLinkMatch() throws Exception {
        SmtpMailService service = newService("https://my.app");

        service.sendEmailVerification("bob@example.com", "Bob",
                "/verify-email?token=xyz");

        MimeMessage msg = greenMail.getReceivedMessages()[0];
        assertThat(msg.getSubject()).isEqualTo("Verify your Life Organizer email");
        assertThat(GreenMailUtil.getBody(msg)).contains("https://my.app/verify-email?token=xyz");
    }

    @Test
    void sendEmailChangeConfirmation_subjectAndLinkMatch() throws Exception {
        SmtpMailService service = newService("https://my.app");

        service.sendEmailChangeConfirmation("carol-new@example.com", "Carol",
                "/confirm-email-change?token=nnn");

        MimeMessage msg = greenMail.getReceivedMessages()[0];
        assertThat(msg.getSubject()).isEqualTo("Confirm your new email address");
        assertThat(GreenMailUtil.getBody(msg)).contains("/confirm-email-change?token=nnn");
    }

    @Test
    void sendAccountRestore_subjectAndLinkMatch() throws Exception {
        SmtpMailService service = newService("https://my.app");

        service.sendAccountRestore("dave@example.com", "Dave",
                "/restore-account?token=rrr");

        MimeMessage msg = greenMail.getReceivedMessages()[0];
        assertThat(msg.getSubject()).isEqualTo("Restore your Life Organizer account");
        assertThat(GreenMailUtil.getBody(msg)).contains("/restore-account?token=rrr");
    }

    @Test
    void smtpFailure_isSwallowed_calledLogsWarn() {
        // Configure the sender to a closed port to force a MailException.
        MailProperties props = new MailProperties();
        props.setProvider("smtp");
        props.setFromAddress("no-reply@example.com");
        props.setFromName("Life Organizer");
        props.setBaseUrl("https://my.app");
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("127.0.0.1");
        sender.setPort(1); // unused port - connection refused
        SmtpMailService service = new SmtpMailService(sender, templateEngine(), props);

        // Must not throw - best-effort delivery contract.
        service.sendPasswordReset("alice@example.com", "Alice",
                "/reset-password?token=abc");
    }

    @Test
    void recipientLine_isExactlyTheCallerEmail() throws Exception {
        SmtpMailService service = newService("https://my.app");

        service.sendEmailVerification("only-this-address@example.com", "X",
                "/verify-email?token=t");

        MimeMessage msg = greenMail.getReceivedMessages()[0];
        Message.RecipientType to = Message.RecipientType.TO;
        assertThat(msg.getRecipients(to)).hasSize(1);
        assertThat(msg.getRecipients(to)[0].toString()).isEqualTo("only-this-address@example.com");
    }

    // ---- helpers ----

    private SmtpMailService newService(String baseUrl) {
        MailProperties props = new MailProperties();
        props.setProvider("smtp");
        props.setFromAddress("no-reply@example.com");
        props.setFromName("Life Organizer");
        props.setBaseUrl(baseUrl);

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(ServerSetupTest.SMTP.getBindAddress());
        sender.setPort(ServerSetupTest.SMTP.getPort());

        return new SmtpMailService(sender, templateEngine(), props);
    }

    private TemplateEngine templateEngine() {
        // SpringTemplateEngine uses SpEL instead of OGNL, so it does not need
        // ognl on the classpath. ClassLoaderTemplateResolver avoids the
        // ApplicationContext dependency that SpringResourceTemplateResolver
        // would require.
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
