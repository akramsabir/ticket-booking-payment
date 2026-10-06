package com.tickethub.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * ALL payment-related settings in ONE place. Spring fills this class from application.yml
 * (everything under "tickethub.payment"), and the secrets come from ENVIRONMENT VARIABLES -
 * never write real keys into a file that goes to Git.
 *
 *   tickethub.payment.gateway            razorpay | mock   (mock = fake provider for tests / no keys)
 *   tickethub.payment.allowed-currencies currencies we accept (Razorpay amounts are in the "smallest unit", paise for INR)
 *   tickethub.payment.razorpay.*         key id, key secret, webhook secret, capture mode
 *   tickethub.payment.sync.*             the background job that asks Razorpay about payments we have not heard back on
 */
@ConfigurationProperties(prefix = "tickethub.payment")
public class PaymentProperties {

    private String gateway = "razorpay";
    private List<String> allowedCurrencies = List.of("INR");
    private Razorpay razorpay = new Razorpay();
    private Sync sync = new Sync();

    public static class Razorpay {
        /** Public key id (rzp_test_xxx / rzp_live_xxx). It is safe to send to the browser - Checkout needs it. */
        private String keyId = "";
        /** SECRET. Signs/verifies checkout results and authenticates our API calls. Never leaves the server. */
        private String keySecret = "";
        /** SECRET #2, set in Dashboard > Webhooks. Different from the key secret. Verifies webhook calls. */
        private String webhookSecret = "";
        /**
         * false (default) = Razorpay auto-captures (the normal Dashboard setting) and we only act on "captured".
         * true = your Razorpay account is set to MANUAL capture, so we call the Capture API ourselves.
         */
        private boolean manualCapture = false;

        public String getKeyId() { return keyId; }
        public void setKeyId(String keyId) { this.keyId = keyId; }
        public String getKeySecret() { return keySecret; }
        public void setKeySecret(String keySecret) { this.keySecret = keySecret; }
        public String getWebhookSecret() { return webhookSecret; }
        public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }
        public boolean isManualCapture() { return manualCapture; }
        public void setManualCapture(boolean manualCapture) { this.manualCapture = manualCapture; }
    }

    public static class Sync {
        private boolean enabled = true;
        /** How often the job runs. */
        private long delayMs = 60_000;
        /** Only look at PENDING payments that have been quiet for at least this long (give webhooks a head start). */
        private long minAgeSeconds = 120;
        /** Stop polling payments older than this (the booking has long expired by then). */
        private long maxAgeMinutes = 120;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getDelayMs() { return delayMs; }
        public void setDelayMs(long delayMs) { this.delayMs = delayMs; }
        public long getMinAgeSeconds() { return minAgeSeconds; }
        public void setMinAgeSeconds(long minAgeSeconds) { this.minAgeSeconds = minAgeSeconds; }
        public long getMaxAgeMinutes() { return maxAgeMinutes; }
        public void setMaxAgeMinutes(long maxAgeMinutes) { this.maxAgeMinutes = maxAgeMinutes; }
    }

    public String getGateway() { return gateway; }
    public void setGateway(String gateway) { this.gateway = gateway; }
    public List<String> getAllowedCurrencies() { return allowedCurrencies; }
    public void setAllowedCurrencies(List<String> allowedCurrencies) { this.allowedCurrencies = allowedCurrencies; }
    public Razorpay getRazorpay() { return razorpay; }
    public void setRazorpay(Razorpay razorpay) { this.razorpay = razorpay; }
    public Sync getSync() { return sync; }
    public void setSync(Sync sync) { this.sync = sync; }
}
