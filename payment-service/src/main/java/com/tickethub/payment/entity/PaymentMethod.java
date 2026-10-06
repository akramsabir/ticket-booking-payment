package com.tickethub.payment.entity;

/**
 * How the customer paid. With Razorpay the customer picks the method INSIDE Razorpay Checkout,
 * so we do not know it when the order is created: it starts as UNSPECIFIED and is filled in from
 * Razorpay's payment record once the payment succeeds.
 */
public enum PaymentMethod {
    UNSPECIFIED,
    CARD,
    UPI,
    NET_BANKING,
    WALLET,
    EMI,
    PAYLATER,
    OTHER;

    /** Converts Razorpay's lowercase method name ("card", "upi", "netbanking", ...) into our enum. */
    public static PaymentMethod fromProvider(String razorpayMethod) {
        if (razorpayMethod == null || razorpayMethod.isBlank()) {
            return UNSPECIFIED;
        }
        return switch (razorpayMethod.toLowerCase()) {
            case "card" -> CARD;
            case "upi" -> UPI;
            case "netbanking" -> NET_BANKING;
            case "wallet" -> WALLET;
            case "emi" -> EMI;
            case "paylater" -> PAYLATER;
            default -> OTHER;
        };
    }
}
