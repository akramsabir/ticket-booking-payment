package com.tickethub.payment.gateway;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * HMAC-SHA256 helpers used to prove "this message really came from Razorpay and was not tampered with".
 *
 * How it works: Razorpay and we share a SECRET. Razorpay computes HMAC(message, secret) and sends the result
 * (the "signature") along. We compute the same thing; if the two match, only someone who knows the secret
 * could have produced it.
 *
 *   Checkout result : message = orderId + "|" + paymentId,  secret = API key secret
 *   Webhook         : message = the RAW request body,       secret = webhook secret
 */
public final class HmacSignatures {

    private HmacSignatures() {
    }

    public static String hmacSha256Hex(byte[] message, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message));
        } catch (GeneralSecurityException e) {
            // HmacSHA256 exists in every JVM, so this means a broken runtime - not a user error
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    public static String hmacSha256Hex(String message, String secret) {
        return hmacSha256Hex(message.getBytes(StandardCharsets.UTF_8), secret);
    }

    /**
     * Compares two signatures in CONSTANT time. A normal equals() stops at the first different character,
     * and an attacker can measure that tiny time difference to guess the signature one character at a time.
     */
    public static boolean matches(String expectedHex, String actualHex) {
        if (expectedHex == null || actualHex == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedHex.getBytes(StandardCharsets.UTF_8),
                actualHex.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }
}
