package com.tickethub.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Typed view of everything under "notification.*" in application.yml. */
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

    private final Mail mail = new Mail();
    private final Retry retry = new Retry();
    private final Kafka kafka = new Kafka();
    private final Security security = new Security();
    private final Events events = new Events();

    public Mail getMail() { return mail; }
    public Retry getRetry() { return retry; }
    public Kafka getKafka() { return kafka; }
    public Security getSecurity() { return security; }
    public Events getEvents() { return events; }

    public static class Mail {
        private String from = "no-reply@tickethub.local";
        private String fromName = "TicketHub";
        private String supportEmail = "support@tickethub.local";
        private String brandName = "TicketHub";
        private List<String> blockedDomains = new ArrayList<>();

        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
        public String getFromName() { return fromName; }
        public void setFromName(String fromName) { this.fromName = fromName; }
        public String getSupportEmail() { return supportEmail; }
        public void setSupportEmail(String supportEmail) { this.supportEmail = supportEmail; }
        public String getBrandName() { return brandName; }
        public void setBrandName(String brandName) { this.brandName = brandName; }
        public List<String> getBlockedDomains() { return blockedDomains; }
        public void setBlockedDomains(List<String> blockedDomains) { this.blockedDomains = blockedDomains; }
    }

    public static class Retry {
        private int maxAttempts = 5;
        private long initialBackoffSeconds = 30;
        private double multiplier = 2.0;
        private long maxBackoffSeconds = 1800;
        private long scanMs = 15000;
        private long leaseSeconds = 120;
        private int batchSize = 50;

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public long getInitialBackoffSeconds() { return initialBackoffSeconds; }
        public void setInitialBackoffSeconds(long v) { this.initialBackoffSeconds = v; }
        public double getMultiplier() { return multiplier; }
        public void setMultiplier(double multiplier) { this.multiplier = multiplier; }
        public long getMaxBackoffSeconds() { return maxBackoffSeconds; }
        public void setMaxBackoffSeconds(long v) { this.maxBackoffSeconds = v; }
        public long getScanMs() { return scanMs; }
        public void setScanMs(long scanMs) { this.scanMs = scanMs; }
        public long getLeaseSeconds() { return leaseSeconds; }
        public void setLeaseSeconds(long leaseSeconds) { this.leaseSeconds = leaseSeconds; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    }

    public static class Kafka {
        private boolean enabled = true;
        private String paymentTopic = "payment-events";
        private String bookingTopic = "booking-events";
        private String groupId = "notification-service";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getPaymentTopic() { return paymentTopic; }
        public void setPaymentTopic(String paymentTopic) { this.paymentTopic = paymentTopic; }
        public String getBookingTopic() { return bookingTopic; }
        public void setBookingTopic(String bookingTopic) { this.bookingTopic = bookingTopic; }
        public String getGroupId() { return groupId; }
        public void setGroupId(String groupId) { this.groupId = groupId; }
    }

    public static class Security {
        private String apiKey = "";

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }

    public static class Events {
        /** key = kebab-case event type (payment-success, booking-confirmed ...). Missing key = enabled. */
        private Map<String, Boolean> enabled = new HashMap<>();

        public Map<String, Boolean> getEnabled() { return enabled; }
        public void setEnabled(Map<String, Boolean> enabled) { this.enabled = enabled; }
    }
}
