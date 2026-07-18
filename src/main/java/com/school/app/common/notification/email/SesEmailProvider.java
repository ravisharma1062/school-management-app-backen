package com.school.app.common.notification.email;

import com.school.app.common.exception.NotConfiguredException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

/**
 * Sends email via Amazon SES's SMTP interface (SES's standard low-friction integration path —
 * no AWS SDK dependency needed). Until {@code SES_SMTP_HOST}/{@code SES_SMTP_USERNAME}/
 * {@code SES_SMTP_PASSWORD} are set, {@link #send} throws {@link NotConfiguredException}.
 */
@Service
public class SesEmailProvider implements EmailProvider {

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String fromAddress;

    public SesEmailProvider(
            @Value("${app.notification.email.smtp.host:}") String host,
            @Value("${app.notification.email.smtp.port:587}") int port,
            @Value("${app.notification.email.smtp.username:}") String username,
            @Value("${app.notification.email.smtp.password:}") String password,
            @Value("${app.notification.email.smtp.from-address:no-reply@school.app}") String fromAddress) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(String toEmail, String subject, String body) {
        if (host.isBlank() || username.isBlank() || password.isBlank()) {
            throw new NotConfiguredException("SES SMTP is not configured (SES_SMTP_HOST/USERNAME/PASSWORD unset)");
        }

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);
        sender.getJavaMailProperties().put("mail.smtp.auth", "true");
        sender.getJavaMailProperties().put("mail.smtp.starttls.enable", "true");
        // JavaMail's default for all three is infinite when unset — a blocked/filtered outbound
        // port (common on PaaS free tiers) or any network-level stall would otherwise hang the
        // request thread forever instead of failing with a clear error.
        sender.getJavaMailProperties().put("mail.smtp.connectiontimeout", "10000");
        sender.getJavaMailProperties().put("mail.smtp.timeout", "10000");
        sender.getJavaMailProperties().put("mail.smtp.writetimeout", "10000");

        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setFrom(fromAddress);
        mailMessage.setTo(toEmail);
        mailMessage.setSubject(subject);
        mailMessage.setText(body);

        try {
            sender.send(mailMessage);
        } catch (MailException e) {
            throw new IllegalStateException("Failed to send email via SES: " + e.getMessage(), e);
        }
    }
}
