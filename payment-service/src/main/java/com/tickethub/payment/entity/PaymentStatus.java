package com.tickethub.payment.entity;

/**
 * The life of ONE payment row (= one Razorpay ORDER). Matches the status list in the TicketHub blueprint.
 *
 *   INITIATED --(Razorpay order created)--> PENDING --(captured)--> SUCCESS --(refund)--> REFUNDED
 *                  |                           |  ^
 *                  |                           v  |  customer retries on the SAME order
 *                  |                         FAILED
 *                  +--(order creation failed)--^
 *
 *   PENDING / FAILED / INITIATED --(booking cancelled or expired)--> CANCELLED
 *
 * IMPORTANT Razorpay detail: one order can have several attempts (card declined, then UPI works).
 * So FAILED means "the LATEST attempt failed" - it is NOT final. A later "captured" still turns it into SUCCESS.
 * Only SUCCESS, REFUNDED and CANCELLED are "settled" states.
 */
public enum PaymentStatus {
    INITIATED,  // our row exists, but Razorpay has not created the order yet
    PENDING,    // Razorpay order exists; waiting for the customer to pay (or for the bank's answer)
    SUCCESS,    // Razorpay CAPTURED the money (verified by signature / webhook / provider lookup)
    FAILED,     // latest attempt failed (card declined, order creation failed ...) - the customer may still retry
    CANCELLED,  // we gave up on this order because the booking was cancelled or expired
    REFUNDED    // money was given back to the customer
}
