package com.tickethub.payment.gateway;

/** What the provider returned after we asked it to create an order (the "bill" the customer will pay). */
public record GatewayOrder(String orderId, long amountInMinorUnits, String currency, String status) {
}
