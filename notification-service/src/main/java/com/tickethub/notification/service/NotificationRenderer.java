package com.tickethub.notification.service;

import com.tickethub.notification.config.NotificationProperties;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns a NotificationRequest into subject + HTML + plain-text.
 * The HTML layout lives in src/main/resources/templates/email/notification.html (edit it to restyle emails);
 * the wording for each event type is in the switch below.
 */
@Component
public class NotificationRenderer {

    private static final String GREEN = "#16a34a";
    private static final String RED = "#dc2626";
    private static final String AMBER = "#d97706";
    private static final String BLUE = "#2563eb";
    private static final String GREY = "#475569";

    private final TemplateEngine templateEngine;
    private final NotificationProperties props;

    public NotificationRenderer(TemplateEngine templateEngine, NotificationProperties props) {
        this.templateEngine = templateEngine;
        this.props = props;
    }

    public RenderedMessage render(NotificationRequest r) {
        String bookingRef = r.getBookingId() == null ? "" : " #" + r.getBookingId();
        String money = formatAmount(r.getAmount(), r.getCurrency());

        String subject;
        String headline;
        String intro;
        String accent;
        List<String> paragraphs = new ArrayList<>();

        switch (r.getType()) {
            case BOOKING_CONFIRMED -> {
                subject = "Your " + brand() + " booking" + bookingRef + " is confirmed";
                headline = "Booking confirmed";
                intro = "Great news - your booking is confirmed and your seats are reserved. Please keep this email for your records.";
                accent = GREEN;
            }
            case PAYMENT_SUCCESS -> {
                subject = "Payment received for booking" + bookingRef;
                headline = "Payment received";
                intro = "We have received your payment" + (money.isEmpty() ? "" : " of " + money)
                        + ". Thank you!";
                accent = GREEN;
            }
            case PAYMENT_FAILED -> {
                subject = "Payment failed for booking" + bookingRef;
                headline = "Payment failed";
                intro = "Unfortunately your payment could not be completed, so this booking is not confirmed. "
                        + "Please try again or use a different payment method.";
                accent = RED;
            }
            case PAYMENT_REFUNDED -> {
                subject = "Refund processed for booking" + bookingRef;
                headline = "Refund processed";
                intro = "Your refund" + (money.isEmpty() ? "" : " of " + money)
                        + " has been processed. It may take a few business days to reach your account.";
                accent = BLUE;
            }
            case BOOKING_CANCELLED -> {
                subject = "Your booking" + bookingRef + " has been cancelled";
                headline = "Booking cancelled";
                intro = "Your booking has been cancelled and the seats have been released.";
                accent = GREY;
            }
            case BOOKING_EXPIRED -> {
                subject = "Your booking" + bookingRef + " has expired";
                headline = "Booking expired";
                intro = "The payment window for this booking ended before payment was completed, "
                        + "so the seats were released. You are welcome to book again.";
                accent = AMBER;
            }
            case CUSTOM -> {
                subject = r.getSubject() == null || r.getSubject().isBlank() ? "Message from " + brand() : r.getSubject();
                headline = subject;
                intro = "";
                accent = BLUE;
                if (r.getMessage() != null) {
                    for (String line : r.getMessage().split("\\r?\\n")) {
                        if (!line.isBlank()) {
                            paragraphs.add(line.trim());
                        }
                    }
                }
            }
            default -> throw new IllegalStateException("Unsupported notification type " + r.getType());
        }

        List<DetailRow> rows = new ArrayList<>();
        if (r.getBookingId() != null) rows.add(new DetailRow("Booking ID", "#" + r.getBookingId()));
        if (r.getPaymentId() != null) rows.add(new DetailRow("Payment reference", "PAY-" + r.getPaymentId()));
        if (notBlank(r.getShowTitle())) rows.add(new DetailRow("Show", r.getShowTitle()));
        if (notBlank(r.getVenue())) rows.add(new DetailRow("Venue", r.getVenue()));
        if (notBlank(r.getShowTime())) rows.add(new DetailRow("Date & time", r.getShowTime()));
        if (r.getSeats() != null && !r.getSeats().isEmpty()) rows.add(new DetailRow("Seats", String.join(", ", r.getSeats())));
        if (!money.isEmpty()) rows.add(new DetailRow("Amount", money));
        if (notBlank(r.getReason())) rows.add(new DetailRow("Reason", r.getReason()));

        String greeting = notBlank(r.getRecipientName()) ? "Hi " + r.getRecipientName().trim() + "," : "Hello,";
        String support = props.getMail().getSupportEmail();

        Context ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("brandName", brand());
        ctx.setVariable("accent", accent);
        ctx.setVariable("headline", headline);
        ctx.setVariable("greeting", greeting);
        ctx.setVariable("intro", intro);
        ctx.setVariable("paragraphs", paragraphs);
        ctx.setVariable("rows", rows);
        ctx.setVariable("supportEmail", support);
        ctx.setVariable("year", Year.now().getValue());
        String html = templateEngine.process("email/notification", ctx);

        StringBuilder text = new StringBuilder();
        text.append(headline).append("\n\n").append(greeting).append("\n\n");
        if (!intro.isEmpty()) text.append(intro).append("\n\n");
        for (String p : paragraphs) text.append(p).append("\n\n");
        for (DetailRow row : rows) text.append(row.getLabel()).append(": ").append(row.getValue()).append("\n");
        if (!rows.isEmpty()) text.append("\n");
        text.append("Need help? Contact us at ").append(support).append(".\n\n- The ").append(brand()).append(" team\n");

        return new RenderedMessage(subject, html, text.toString());
    }

    private String brand() {
        return props.getMail().getBrandName();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    static String formatAmount(BigDecimal amount, String currency) {
        if (amount == null) {
            return "";
        }
        String number = amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        String code = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        return switch (code) {
            case "INR" -> "\u20B9" + number;
            case "USD" -> "$" + number;
            case "EUR" -> "\u20AC" + number;
            case "GBP" -> "\u00A3" + number;
            case "" -> number;
            default -> code + " " + number;
        };
    }
}
