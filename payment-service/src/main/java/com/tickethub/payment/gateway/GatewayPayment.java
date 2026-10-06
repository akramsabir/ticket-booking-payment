package com.tickethub.payment.gateway;

/**
 * A provider-side payment ATTEMPT, translated into our own plain type so that nothing outside the gateway
 * package needs to know about Razorpay's JSON or SDK classes.
 *
 * status is Razorpay's own word: created | authorized | captured | refunded | failed
 */
public record GatewayPayment(
        String id,
        String orderId,
        String status,
        String method,
        long amountInMinorUnits,
        String currency,
        String errorDescription,
        long amountRefundedInMinorUnits,
        String refundStatus,
        long createdAtEpochSeconds) {

    public boolean isCaptured() {
        return "captured".equalsIgnoreCase(status);
    }

    public boolean isAuthorized() {
        return "authorized".equalsIgnoreCase(status);
    }

    public boolean isFailed() {
        return "failed".equalsIgnoreCase(status);
    }

    /** True once Razorpay has returned the whole amount to the customer. */
    public boolean isFullyRefunded() {
        return "full".equalsIgnoreCase(refundStatus)
                || (amountRefundedInMinorUnits > 0 && amountRefundedInMinorUnits >= amountInMinorUnits);
    }
}
