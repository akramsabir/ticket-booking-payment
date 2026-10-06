package com.tickethub.notification.channel;

import com.tickethub.notification.config.NotificationProperties;
import com.tickethub.notification.entity.NotificationChannelType;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;

/** Sends HTML + plain-text email through the configured SMTP server (spring.mail.*). */
@Component
public class EmailChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailChannel.class);

    private final JavaMailSender mailSender;
    private final NotificationProperties props;

    public EmailChannel(JavaMailSender mailSender, NotificationProperties props) {
        this.mailSender = mailSender;
        this.props = props;
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.EMAIL;
    }

    @Override
    public String send(ChannelMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");

            helper.setFrom(new InternetAddress(props.getMail().getFrom(), clean(props.getMail().getFromName()), "UTF-8"));

            InternetAddress to = new InternetAddress(message.recipient().trim(), true); // strict check
            if (message.recipientName() != null && !message.recipientName().isBlank()) {
                to.setPersonal(clean(message.recipientName()), "UTF-8");
            }
            helper.setTo(to);
            helper.setSubject(clean(message.subject()));
            helper.setText(message.textBody() == null ? "" : message.textBody(), message.htmlBody());

            mailSender.send(mime);

            String messageId = mime.getMessageID();
            log.info("Email sent to {} (messageId={})", maskEmail(message.recipient()), messageId);
            return messageId;

        } catch (AddressException ex) {
            throw new ChannelDeliveryException("Invalid email address: " + ex.getMessage(), false, ex);
        } catch (MailParseException ex) {
            throw new ChannelDeliveryException("Email could not be built: " + ex.getMessage(), false, ex);
        } catch (MailAuthenticationException ex) {
            throw new ChannelDeliveryException(
                    "SMTP login failed - check MAIL_USERNAME / MAIL_PASSWORD: " + ex.getMessage(), true, ex);
        } catch (MailException ex) {
            throw new ChannelDeliveryException("SMTP error: " + rootMessage(ex), true, ex);
        } catch (MessagingException | UnsupportedEncodingException ex) {
            throw new ChannelDeliveryException("Email could not be built: " + ex.getMessage(), false, ex);
        }
    }

    /** Remove line breaks so a value can never inject extra mail headers. */
    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        return msg == null ? root.getClass().getSimpleName() : msg;
    }

    /** a***@gmail.com - keeps personal data out of the logs. */
    public static String maskEmail(String email) {
        if (email == null) {
            return "null";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? email.substring(at) : "");
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
