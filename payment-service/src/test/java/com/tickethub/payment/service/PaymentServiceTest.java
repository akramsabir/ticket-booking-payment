package com.tickethub.payment.service;

import com.tickethub.payment.config.PaymentProperties;
import com.tickethub.payment.dto.PaymentEvent;
import com.tickethub.payment.dto.PaymentRequest;
import com.tickethub.payment.dto.PaymentResponse;
import com.tickethub.payment.dto.VerifyPaymentRequest;
import com.tickethub.payment.entity.PaymentMethod;
import com.tickethub.payment.entity.PaymentStatus;
import com.tickethub.payment.exception.DuplicatePaymentException;
import com.tickethub.payment.exception.IdempotencyKeyReuseException;
import com.tickethub.payment.exception.InvalidPaymentRequestException;
import com.tickethub.payment.exception.InvalidPaymentStateException;
import com.tickethub.payment.exception.InvalidSignatureException;
import com.tickethub.payment.exception.PaymentNotFoundException;
import com.tickethub.payment.exception.PaymentProviderException;
import com.tickethub.payment.exception.PaymentVerificationException;
import com.tickethub.payment.kafka.PaymentEventPublisher;
import com.tickethub.payment.support.FakeGateway;
import com.tickethub.payment.support.FakePaymentDb;
import com.tickethub.payment.support.TestTx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * UNIT TEST of the business logic. No database, no Kafka, no Razorpay, no web server:
 * an in-memory payments table (FakePaymentDb) and a scriptable fake Razorpay (FakeGateway) stand in,
 * so the tests run whole scenarios of the TicketHub blueprint in milliseconds.
 */
class PaymentServiceTest {

    private FakePaymentDb db;
    private FakeGateway gateway;
    private PaymentEventPublisher publisher;
    private PaymentProperties properties;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        db = new FakePaymentDb();
        gateway = new FakeGateway();
        publisher = mock(PaymentEventPublisher.class);
        properties = new PaymentProperties();
        service = new PaymentService(db.repository, gateway, publisher, TestTx.noOp(), properties);
    }

    private PaymentRequest request(long bookingId, String amount, String key) {
        return new PaymentRequest(bookingId, 7L, "asha@example.com", new BigDecimal(amount), "INR", key);
    }

    private VerifyPaymentRequest proof(String orderId, String paymentId) {
        return new VerifyPaymentRequest(orderId, paymentId, FakeGateway.signCheckout(orderId, paymentId));
    }

    private List<PaymentEvent> publishedEvents() {
        ArgumentCaptor<PaymentEvent> captor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(publisher, atLeast(0)).publish(captor.capture());
        return captor.getAllValues();
    }

    // ------------------------------------------------------------------------------------ create order

    @Test
    void createPayment_createsPendingOrderWithEverythingCheckoutNeeds() {
        PaymentResponse r = service.createPayment(request(101, "500.00", "k1"));

        assertEquals(PaymentStatus.PENDING, r.status());
        assertEquals("order_1", r.providerOrderId());
        assertEquals("rzp_test_fake", r.razorpayKeyId());
        assertEquals(50000L, r.amountInMinorUnits());          // paise
        assertEquals(PaymentMethod.UNSPECIFIED, r.paymentMethod());
        assertEquals(1, gateway.ordersCreated);
        assertTrue(publishedEvents().isEmpty(), "nothing is announced until the money actually arrives");
    }

    @Test
    void createPayment_sameIdempotencyKey_returnsSameOrderWithoutCreatingAnother() {
        PaymentResponse first = service.createPayment(request(101, "500.00", "k1"));
        PaymentResponse second = service.createPayment(request(101, "500.00", "k1"));

        assertEquals(first.paymentId(), second.paymentId());
        assertEquals(first.providerOrderId(), second.providerOrderId());
        assertEquals(1, gateway.ordersCreated);
    }

    @Test
    void createPayment_sameKeyDifferentAmount_isRefused() {
        service.createPayment(request(101, "500.00", "k1"));

        assertThrows(IdempotencyKeyReuseException.class,
                () -> service.createPayment(request(101, "999.00", "k1")));
        assertEquals(1, gateway.ordersCreated);
    }

    @Test
    void createPayment_bookingAlreadyHasOpenOrder_reusesItEvenWithNewKey() {
        PaymentResponse first = service.createPayment(request(101, "500.00", "k1"));
        PaymentResponse second = service.createPayment(request(101, "500.00", "another-key"));

        assertEquals(first.paymentId(), second.paymentId());
        assertEquals(1, gateway.ordersCreated, "two open orders could both be paid = double charge");
    }

    @Test
    void createPayment_bookingAlreadyPaid_isRefused() {
        PaymentResponse created = service.createPayment(request(101, "500.00", "k1"));
        gateway.givenPayment("pay_1", created.providerOrderId(), "captured", 50000);
        service.verifyPayment(proof(created.providerOrderId(), "pay_1"));

        assertThrows(DuplicatePaymentException.class,
                () -> service.createPayment(request(101, "500.00", "k2")));
    }

    @Test
    void createPayment_unsupportedCurrency_isRefusedBeforeAnythingIsSaved() {
        PaymentRequest usd = new PaymentRequest(101L, 7L, "asha@example.com", new BigDecimal("5.00"), "USD", "k1");

        assertThrows(InvalidPaymentRequestException.class, () -> service.createPayment(usd));
        assertTrue(db.all().isEmpty());
        assertEquals(0, gateway.ordersCreated);
    }

    @Test
    void createPayment_razorpayDown_marksFailed_andRetryWithSameKeyRecoversOnSameRow() {
        gateway.failOrderCreation = true;
        assertThrows(PaymentProviderException.class, () -> service.createPayment(request(101, "500.00", "k1")));
        assertEquals(1, db.all().size());
        assertEquals(PaymentStatus.FAILED, db.all().get(0).getStatus());
        assertNull(db.all().get(0).getProviderOrderId());

        gateway.failOrderCreation = false;
        PaymentResponse retry = service.createPayment(request(101, "500.00", "k1"));

        assertEquals(PaymentStatus.PENDING, retry.status());
        assertEquals(1, db.all().size(), "the retry reuses the same row");
        assertEquals(1, gateway.ordersCreated);
    }

    // ------------------------------------------------------------------------------------ verify

    @Test
    void verify_validProofAndCapturedPayment_becomesSuccessAndPublishesOnce() {
        PaymentResponse created = service.createPayment(request(101, "500.00", "k1"));
        gateway.givenPayment("pay_1", created.providerOrderId(), "captured", 50000);

        PaymentResponse r = service.verifyPayment(proof(created.providerOrderId(), "pay_1"));

        assertEquals(PaymentStatus.SUCCESS, r.status());
        assertEquals("pay_1", r.providerPaymentId());
        assertEquals(PaymentMethod.UPI, r.paymentMethod());    // learned from Razorpay, not guessed
        assertNull(r.razorpayKeyId(), "a finished payment no longer needs the checkout key");
        List<PaymentEvent> events = publishedEvents();
        assertEquals(1, events.size());
        assertEquals("PAYMENT_SUCCESS", events.get(0).eventType());
        assertEquals("asha@example.com", events.get(0).customerEmail());
    }

    @Test
    void verify_sameProofTwice_isHarmlessAndPublishesOnlyOneEvent() {
        PaymentResponse created = service.createPayment(request(101, "500.00", "k1"));
        gateway.givenPayment("pay_1", created.providerOrderId(), "captured", 50000);

        service.verifyPayment(proof(created.providerOrderId(), "pay_1"));
        PaymentResponse again = service.verifyPayment(proof(created.providerOrderId(), "pay_1"));

        assertEquals(PaymentStatus.SUCCESS, again.status());
        assertEquals(1, publishedEvents().size());
    }

    @Test
    void verify_wrongSignature_isRejectedAndNothingChanges() {
        PaymentResponse created = service.createPayment(request(101, "500.00", "k1"));
        gateway.givenPayment("pay_1", created.providerOrderId(), "captured", 50000);

        VerifyPaymentRequest forged = new VerifyPaymentRequest(created.providerOrderId(), "pay_1", "deadbeef");

        assertThrows(InvalidSignatureException.class, () -> service.verifyPayment(forged));
        assertEquals(PaymentStatus.PENDING, db.row(created.paymentId()).getStatus());
        assertEquals(0, gateway.fetchCalls, "a forged proof must not even cost us a Razorpay call");
        assertTrue(publishedEvents().isEmpty());
    }

    @Test
    void verify_signatureForADifferentPayment_isRejected() {
        PaymentResponse created = service.createPayment(request(101, "500.00", "k1"));
        // valid signature, but for pay_OTHER - the attacker tries to claim pay_1
        VerifyPaymentRequest mixed = new VerifyPaymentRequest(created.providerOrderId(), "pay_1",
                FakeGateway.signCheckout(created.providerOrderId(), "pay_OTHER"));

        assertThrows(InvalidSignatureException.class, () -> service.verifyPayment(mixed));
    }

    @Test
    void verify_razorpayReportsDifferentAmount_isRefusedAndStaysPending() {
        PaymentResponse created = service.createPayment(request(101, "500.00", "k1"));
        gateway.givenPayment("pay_1", created.providerOrderId(), "captured", 100);   // only Rs 1 captured!

        assertThrows(PaymentVerificationException.class,
                () -> service.verifyPayment(proof(created.providerOrderId(), "pay_1")));
        assertEquals(PaymentStatus.PENDING, db.row(created.paymentId()).getStatus());
        assertTrue(publishedEvents().isEmpty());
    }

    @Test
    void verify_paymentBelongsToAnotherOrder_isRefused() {
        PaymentResponse created = service.createPayment(request(101, "500.00", "k1"));
        gateway.givenPayment("pay_1", "order_SOMEONE_ELSE", "captured", 50000);

        assertThrows(PaymentVerificationException.class,
                () -> service.verifyPayment(proof(created.providerOrderId(), "pay_1")));
        assertEquals(PaymentStatus.PENDING, db.row(created.paymentId()).getStatus());
    }

    @Test
    void verify_authorizedButNotYetCaptured_staysPendingWhenRazorpayAutoCaptures() {
        PaymentResponse created = service.createPayment(request(101, "500.00", "k1"));
        gateway.givenPayment("pay_1", created.providerOrderId(), "authorized", 50000);

        PaymentResponse r = service.verifyPayment(proof(created.providerOrderId(), "pay_1"));

        assertEquals(PaymentStatus.PENDING, r.status(), "SUCCESS only when the money is captured");
        assertTrue(publishedEvents().isEmpty());
    }

    @Test
    void verify_authorizedWithManualCapture_isCapturedByUs() {
        properties.getRazorpay().setManualCapture(true);
        PaymentResponse created = service.createPayment(request(101, "500.00", "k1"));
        gateway.givenPayment("pay_1", created.providerOrderId(), "authorized", 50000);

        PaymentResponse r = service.verifyPayment(proof(created.providerOrderId(), "pay_1"));

        assertEquals(PaymentStatus.SUCCESS, r.status());
    }

    @Test
    void verify_unknownOrder_isNotFound() {
        assertThrows(PaymentNotFoundException.class,
                () -> service.verifyPayment(proof("order_nope", "pay_1")));
    }

    @Test
    void failedAttemptThenSuccessfulRetryOnSameOrder_endsAsSuccess() {
        PaymentResponse created = service.createPayment(request(101, "500.00", "k1"));
        String order = created.providerOrderId();

        // attempt 1 (card) failed - we learn it through reconciliation
        gateway.orderPayments.put(order, List.of(FakeGateway.payment("pay_bad", order, "failed", 50000)));
        PaymentResponse afterFailure = service.syncPayment(created.paymentId());
        assertEquals(PaymentStatus.FAILED, afterFailure.status());
        assertEquals("Payment failed (simulated)", afterFailure.failureReason());
        assertNotNull(afterFailure.razorpayKeyId(), "a FAILED payment can still be retried, so the key stays");

        // attempt 2 (UPI) succeeds on the same order
        gateway.givenPayment("pay_good", order, "captured", 50000);
        PaymentResponse afterRetry = service.verifyPayment(proof(order, "pay_good"));

        assertEquals(PaymentStatus.SUCCESS, afterRetry.status());
        assertEquals("pay_good", afterRetry.providerPaymentId());
        assertNull(afterRetry.failureReason());
    }

    // ------------------------------------------------------------------------------------ cancel

    @Test
    void cancelForBooking_unpaid_closesThePaymentAndAnnouncesNothing() {
        PaymentResponse created = service.createPayment(request(101, "500.00", "k1"));

        List<PaymentResponse> result = service.cancelPaymentsForBooking(101L);

        assertEquals(1, result.size());
        assertEquals(PaymentStatus.CANCELLED, result.get(0).status());
        assertEquals(PaymentStatus.CANCELLED, db.row(created.paymentId()).getStatus());
        assertTrue(publishedEvents().isEmpty());
    }

    @Test
    void cancelForBooking_customerPaidASecondAgo_isNotCancelled_itBecomesSuccess() {
        PaymentResponse created = service.createPayment(request(101, "500.00", "k1"));
        // The webhook has not reached us yet, but Razorpay already has the captured payment
        gateway.orderPayments.put(created.providerOrderId(),
                List.of(FakeGateway.payment("pay_1", created.providerOrderId(), "captured", 50000)));

        List<PaymentResponse> result = service.cancelPaymentsForBooking(101L);

        assertEquals(PaymentStatus.SUCCESS, result.get(0).status());
        assertEquals("PAYMENT_SUCCESS", publishedEvents().get(0).eventType());
    }

    @Test
    void cancelForBooking_razorpayUnreachable_stillCancels() {
        PaymentResponse created = service.createPayment(request(101, "500.00", "k1"));
        FakeGateway down = new FakeGateway() {
            @Override
            public java.util.List<com.tickethub.payment.gateway.GatewayPayment> fetchOrderPayments(String orderId) {
                throw new PaymentProviderException("timeout");
            }
        };
        PaymentService svc = new PaymentService(db.repository, down, publisher, TestTx.noOp(), properties);

        List<PaymentResponse> result = svc.cancelPaymentsForBooking(101L);

        assertEquals(PaymentStatus.CANCELLED, result.get(0).status());
    }

    @Test
    void moneyArrivesAfterCancellation_isRefundedAutomatically_andCustomerGetsOnlyTheRefundMail() {
        // SC-12: the reservation expired (payment CANCELLED), then the customer's slow UPI payment lands anyway
        PaymentResponse created = service.createPayment(request(101, "500.00", "k1"));
        service.cancelPaymentsForBooking(101L);
        gateway.givenPayment("pay_late", created.providerOrderId(), "captured", 50000);

        PaymentResponse r = service.verifyPayment(proof(created.providerOrderId(), "pay_late"));

        assertEquals(PaymentStatus.REFUNDED, r.status());
        assertEquals(1, gateway.refundsRequested);
        List<PaymentEvent> events = publishedEvents();
        assertEquals(1, events.size(), "no 'payment successful' mail for a booking that no longer exists");
        assertEquals("PAYMENT_REFUNDED", events.get(0).eventType());
    }

    // ------------------------------------------------------------------------------------ refund

    private long paidPayment() {
        PaymentResponse created = service.createPayment(request(101, "500.00", "k1"));
        gateway.givenPayment("pay_1", created.providerOrderId(), "captured", 50000);
        service.verifyPayment(proof(created.providerOrderId(), "pay_1"));
        reset(publisher);
        return created.paymentId();
    }

    @Test
    void refund_successfulPayment_becomesRefundedAndPublishesEvent() {
        long id = paidPayment();

        PaymentResponse r = service.refundPayment(id);

        assertEquals(PaymentStatus.REFUNDED, r.status());
        assertNotNull(db.row(id).getProviderRefundId());
        assertEquals("PAYMENT_REFUNDED", publishedEvents().get(0).eventType());
    }

    @Test
    void refund_twice_talksToRazorpayOnlyOnce() {
        long id = paidPayment();

        service.refundPayment(id);
        PaymentResponse again = service.refundPayment(id);

        assertEquals(PaymentStatus.REFUNDED, again.status());
        assertEquals(1, gateway.refundsRequested);
        assertEquals(1, publishedEvents().size());
    }

    @Test
    void refund_razorpayRejectsBecauseItWasAlreadyRefunded_syncsOurRecordInsteadOfFailing() {
        long id = paidPayment();
        gateway.refundThrows = true;
        gateway.payments.put("pay_1", new com.tickethub.payment.gateway.GatewayPayment(
                "pay_1", "order_1", "refunded", "upi", 50000, "INR", null, 50000, "full", 1));

        PaymentResponse r = service.refundPayment(id);

        assertEquals(PaymentStatus.REFUNDED, r.status());
    }

    @Test
    void refund_razorpayRejects_andNothingWasRefunded_failsAndKeepsSuccess() {
        long id = paidPayment();
        gateway.refundThrows = true;

        assertThrows(PaymentProviderException.class, () -> service.refundPayment(id));
        assertEquals(PaymentStatus.SUCCESS, db.row(id).getStatus());
    }

    @Test
    void refund_paymentThatWasNeverPaid_isInvalidState() {
        PaymentResponse created = service.createPayment(request(101, "500.00", "k1"));

        assertThrows(InvalidPaymentStateException.class, () -> service.refundPayment(created.paymentId()));
        assertEquals(0, gateway.refundsRequested);
    }

    @Test
    void refundForBooking_findsTheSuccessfulPayment() {
        paidPayment();

        assertEquals(PaymentStatus.REFUNDED, service.refundForBooking(101L).status());
        assertEquals(PaymentStatus.REFUNDED, service.refundForBooking(101L).status());   // repeat is harmless
        assertEquals(1, gateway.refundsRequested);
    }

    @Test
    void refundForBooking_withoutAnyPayment_isInvalidState() {
        assertThrows(InvalidPaymentStateException.class, () -> service.refundForBooking(999L));
    }

    // ------------------------------------------------------------------------------------ reads

    @Test
    void getPayment_unknownId_throwsNotFound() {
        assertThrows(PaymentNotFoundException.class, () -> service.getPayment(99L));
    }

    @Test
    void getPaymentsForBooking_returnsOnlyThatBookingsPayments() {
        service.createPayment(request(101, "500.00", "k1"));
        assertEquals(1, service.getPaymentsForBooking(101L).size());
        assertTrue(service.getPaymentsForBooking(555L).isEmpty());
        verify(publisher, never()).publish(any());
    }
}
