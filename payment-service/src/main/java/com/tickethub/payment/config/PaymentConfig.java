package com.tickethub.payment.config;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wires the payment settings and (only when we really talk to Razorpay) the Razorpay SDK client.
 *
 * Why the RazorpayClient bean is conditional: with tickethub.payment.gateway=mock there are no keys,
 * and we must not try to build a real client (or fail at startup) for a fake provider.
 *
 * Why we fail FAST on missing keys: it is much better to see
 *   "RAZORPAY_KEY_ID is not set"
 * when the app starts than a confusing 502 on the first customer payment.
 */
@Configuration
@EnableConfigurationProperties(PaymentProperties.class)
@EnableScheduling   // needed for the reconciliation job (PaymentSyncJob)
public class PaymentConfig {

    private static final Logger log = LoggerFactory.getLogger(PaymentConfig.class);

    @Bean
    @ConditionalOnProperty(name = "tickethub.payment.gateway", havingValue = "razorpay", matchIfMissing = true)
    public RazorpayClient razorpayClient(PaymentProperties props) throws RazorpayException {
        PaymentProperties.Razorpay rz = props.getRazorpay();
        require(rz.getKeyId(), "RAZORPAY_KEY_ID (tickethub.payment.razorpay.key-id)");
        require(rz.getKeySecret(), "RAZORPAY_KEY_SECRET (tickethub.payment.razorpay.key-secret)");
        require(rz.getWebhookSecret(), "RAZORPAY_WEBHOOK_SECRET (tickethub.payment.razorpay.webhook-secret)");

        // rzp_test_... keys = Test Mode (no real money). rzp_live_... keys = real money. Make it obvious in the log.
        String mode = rz.getKeyId().startsWith("rzp_live_") ? "LIVE (real money!)" : "TEST";
        log.info("Razorpay client initialised in {} mode (keyId={})", mode, rz.getKeyId());
        return new RazorpayClient(rz.getKeyId(), rz.getKeySecret());
    }

    private static void require(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(what + " is not set. Set the environment variable, "
                    + "or run with PAYMENT_GATEWAY=mock for local work without Razorpay.");
        }
    }
}
