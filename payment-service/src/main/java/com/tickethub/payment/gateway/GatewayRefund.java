package com.tickethub.payment.gateway;

/** The provider's answer to a refund request. status: pending | processed | failed */
public record GatewayRefund(String refundId, String paymentId, long amountInMinorUnits, String status) {
}
