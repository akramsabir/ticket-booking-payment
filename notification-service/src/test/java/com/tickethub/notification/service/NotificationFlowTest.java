package com.tickethub.notification.service;

import com.tickethub.notification.entity.NotificationRecord;
import com.tickethub.notification.entity.NotificationStatus;
import com.tickethub.notification.entity.NotificationType;
import com.tickethub.notification.exception.InvalidEventException;
import com.tickethub.notification.repository.NotificationRepository;
import com.tickethub.notification.repository.UserContactRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** End-to-end tests of the service layer against an in-memory H2 database and a mocked SMTP sender. */
@SpringBootTest
@ActiveProfiles("test")
class NotificationFlowTest {

    private static final String PAYMENT_SUCCESS = """
            {"eventType":"PAYMENT_SUCCESS","paymentId":11,"bookingId":5,"userId":7,
             "customerEmail":"asha@tickethub.test","amount":700.00,"currency":"INR","status":"SUCCESS",
             "failureReason":null,"occurredAt":1727900000.123456789}""";

    private static final String BOOKING_CONFIRMED = """
            {"bookingId":5,"customerId":7,"showId":3,"status":"CONFIRMED","totalAmount":"700.00"}""";

    @Autowired NotificationService service;
    @Autowired EventNotificationHandler handler;
    @Autowired NotificationRenderer renderer;
    @Autowired NotificationRepository repository;
    @Autowired UserContactRepository contactRepository;
    @MockBean JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        contactRepository.deleteAll();
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void customEmail_isSentAndRecorded() throws Exception {
        NotificationRequest request = new NotificationRequest(NotificationType.CUSTOM, "CUSTOM:t1")
                .recipient("fan@tickethub.test").recipientName("Fan")
                .subject("Hello there").message("Line one\nLine two");

        SubmitResult result = service.submit(request);

        assertTrue(result.created());
        assertEquals(NotificationStatus.SENT, result.record().getStatus());
        assertEquals(1, result.record().getAttempts());
        assertNotNull(result.record().getSentAt());

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(sent.capture());
        assertEquals("Hello there", sent.getValue().getSubject());
        assertTrue(sent.getValue().getAllRecipients()[0].toString().contains("fan@tickethub.test"));
    }

    @Test
    void sameDedupeKey_sendsOnlyOneEmail() {
        NotificationRequest request = new NotificationRequest(NotificationType.CUSTOM, "CUSTOM:dup")
                .recipient("fan@tickethub.test").subject("Hi").message("Body");

        SubmitResult first = service.submit(request);
        SubmitResult second = service.submit(request);

        assertTrue(first.created());
        assertFalse(second.created());
        assertEquals(first.record().getNotificationId(), second.record().getNotificationId());
        verify(mailSender, times(1)).send(any(MimeMessage.class));
        assertEquals(1, repository.count());
    }

    @Test
    void smtpFailure_isRecordedAndRetriedLater() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        SubmitResult result = service.submit(new NotificationRequest(NotificationType.CUSTOM, "CUSTOM:retry")
                .recipient("fan@tickethub.test").subject("Hi").message("Body"));

        NotificationRecord failed = result.record();
        assertEquals(NotificationStatus.FAILED, failed.getStatus());
        assertEquals(1, failed.getAttempts());
        assertNotNull(failed.getNextRetryAt());
        assertNotNull(failed.getLastError());

        // SMTP comes back; the retry time arrives
        doNothing().when(mailSender).send(any(MimeMessage.class));
        failed.setNextRetryAt(Instant.now().minusSeconds(1));
        repository.save(failed);

        assertEquals(1, service.processDue());

        NotificationRecord retried = service.get(failed.getNotificationId());
        assertEquals(NotificationStatus.SENT, retried.getStatus());
        assertEquals(2, retried.getAttempts());
        assertNull(retried.getNextRetryAt());
    }

    @Test
    void paymentSuccessEvent_sendsReceipt_andLearnsTheCustomersEmail() {
        handler.handlePaymentEvent(PAYMENT_SUCCESS);

        List<NotificationRecord> all = repository.findAll();
        assertEquals(1, all.size());
        assertEquals(NotificationType.PAYMENT_SUCCESS, all.get(0).getType());
        assertEquals(NotificationStatus.SENT, all.get(0).getStatus());
        assertEquals("asha@tickethub.test", all.get(0).getRecipient());
        assertTrue(contactRepository.findById(7L).isPresent());
    }

    @Test
    void bookingConfirmedEvent_usesTheEmailLearnedFromThePayment() {
        handler.handlePaymentEvent(PAYMENT_SUCCESS);
        handler.handleBookingEvent(BOOKING_CONFIRMED);

        NotificationRecord booking = repository.findByDedupeKey("BOOKING:CONFIRMED:5").orElseThrow();
        assertEquals(NotificationType.BOOKING_CONFIRMED, booking.getType());
        assertEquals(NotificationStatus.SENT, booking.getStatus());
        assertEquals("asha@tickethub.test", booking.getRecipient());
        assertTrue(booking.getHtmlBody().contains("700.00"));
    }

    @Test
    void bookingConfirmedEvent_showsTheShowAndSeatsInTheEmail() {
        handler.handleBookingEvent("""
                {"bookingId":5,"customerId":7,"showId":3,"status":"CONFIRMED","totalAmount":"700.00",
                 "customerEmail":"asha@tickethub.test","showTitle":"Inception","venue":"PVR Phoenix",
                 "showTime":"Sun 18 Oct 2026, 19:30","seats":["A5","A6"]}""");

        NotificationRecord booking = repository.findByDedupeKey("BOOKING:CONFIRMED:5").orElseThrow();
        assertEquals(NotificationStatus.SENT, booking.getStatus());
        assertEquals("asha@tickethub.test", booking.getRecipient());
        assertTrue(booking.getHtmlBody().contains("Inception"));
        assertTrue(booking.getHtmlBody().contains("PVR Phoenix"));
        assertTrue(booking.getHtmlBody().contains("A5, A6"));
        assertTrue(booking.getHtmlBody().contains("700.00"));
    }

    @Test
    void duplicateKafkaDelivery_isSuppressed() {
        handler.handleBookingEvent("""
                {"bookingId":5,"customerId":7,"status":"CONFIRMED","totalAmount":"700.00","customerEmail":"asha@tickethub.test"}""");
        handler.handleBookingEvent("""
                {"bookingId":5,"customerId":7,"status":"CONFIRMED","totalAmount":"700.00","customerEmail":"asha@tickethub.test"}""");

        assertEquals(1, repository.count());
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void unknownRecipient_isRecordedAsFailed_andCanBeFixedByRetry() {
        handler.handleBookingEvent("""
                {"bookingId":9,"customerId":999,"status":"CONFIRMED","totalAmount":"100.00"}""");

        NotificationRecord record = repository.findByDedupeKey("BOOKING:CONFIRMED:9").orElseThrow();
        assertEquals(NotificationStatus.FAILED, record.getStatus());
        assertNull(record.getNextRetryAt());
        verify(mailSender, never()).send(any(MimeMessage.class));

        NotificationRecord fixed = service.retryNow(record.getNotificationId(), "late@tickethub.test");
        assertEquals(NotificationStatus.SENT, fixed.getStatus());
        assertEquals("late@tickethub.test", fixed.getRecipient());
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void pendingBookingAndInitiatedPayment_areIgnored() {
        handler.handleBookingEvent("""
                {"bookingId":5,"customerId":7,"status":"PENDING","totalAmount":"700.00"}""");
        handler.handlePaymentEvent("""
                {"eventType":"PAYMENT_INITIATED","paymentId":1,"bookingId":5,"userId":7,"customerEmail":"asha@tickethub.test"}""");

        assertEquals(0, repository.count());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void malformedMessage_isRejectedAsInvalidEvent() {
        assertThrows(InvalidEventException.class, () -> handler.handlePaymentEvent("this is not json"));
        assertThrows(InvalidEventException.class, () -> handler.handleBookingEvent("{\"status\":\"CONFIRMED\"}"));
    }

    @Test
    void renderer_buildsReadableEmail_andEscapesHtml() {
        RenderedMessage message = renderer.render(new NotificationRequest(NotificationType.BOOKING_CONFIRMED, "k")
                .recipientName("<b>Evil</b>")
                .bookingId(42L).amount(new BigDecimal("1250")).currency("INR")
                .showTitle("Inception").seats(List.of("A1", "A2")));

        assertEquals("Your TicketHub booking #42 is confirmed", message.subject());
        assertTrue(message.htmlBody().contains("A1, A2"));
        assertTrue(message.htmlBody().contains("\u20B91250.00"));
        assertFalse(message.htmlBody().contains("<b>Evil</b>"));
        assertTrue(message.textBody().contains("Inception"));
    }
}
