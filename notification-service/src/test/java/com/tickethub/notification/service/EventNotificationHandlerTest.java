package com.tickethub.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickethub.notification.entity.NotificationRecord;
import com.tickethub.notification.entity.NotificationType;
import com.tickethub.notification.exception.InvalidEventException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The booking-confirmed email starts here: a Kafka message from booking-service goes in, a NotificationRequest comes out.
 * No Spring, database or SMTP: the two services the handler talks to are mocks.
 */
class EventNotificationHandlerTest {

    private NotificationService notificationService;
    private UserContactService contacts;
    private EventNotificationHandler handler;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        contacts = mock(UserContactService.class);
        handler = new EventNotificationHandler(new ObjectMapper(), notificationService, contacts);
        when(notificationService.isEnabled(any())).thenReturn(true);
        when(notificationService.submit(any())).thenReturn(new SubmitResult(mock(NotificationRecord.class), true));
    }

    private NotificationRequest submitted() {
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).submit(captor.capture());
        return captor.getValue();
    }

    // ------------------------------------------------------------------ the confirmation email

    @Test
    void bookingConfirmed_withEverything_buildsTheFullConfirmationEmailRequest() {
        handler.handleBookingEvent("""
                {"bookingId":5,"customerId":42,"showId":100,"status":"CONFIRMED","totalAmount":"700.00",
                 "customerEmail":"ravi@example.com","showTitle":"Inception","venue":"PVR Phoenix",
                 "showTime":"Sun 18 Oct 2026, 19:30","seats":["A5","A6"]}""");

        NotificationRequest r = submitted();
        assertEquals(NotificationType.BOOKING_CONFIRMED, r.getType());
        assertEquals("BOOKING:CONFIRMED:5", r.getDedupeKey(), "same booking twice = one email");
        assertEquals("ravi@example.com", r.getRecipient());
        assertEquals(5L, r.getBookingId());
        assertEquals(new BigDecimal("700.00"), r.getAmount());
        assertEquals("Inception", r.getShowTitle());
        assertEquals("PVR Phoenix", r.getVenue());
        assertEquals("Sun 18 Oct 2026, 19:30", r.getShowTime());
        assertEquals(List.of("A5", "A6"), r.getSeats());
        verify(contacts).remember(42L, "ravi@example.com");
    }

    @Test
    void bookingConfirmed_withoutEmailInTheEvent_fallsBackToTheAddressLearnedFromPayments() {
        when(contacts.findEmail(42L)).thenReturn(Optional.of("learned@example.com"));

        handler.handleBookingEvent("{\"bookingId\":5,\"customerId\":42,\"status\":\"CONFIRMED\",\"totalAmount\":\"700.00\"}");

        assertEquals("learned@example.com", submitted().getRecipient());
    }

    @Test
    void bookingConfirmed_withOnlyTheBasics_stillWorks_andLeavesTheDetailsEmpty() {
        handler.handleBookingEvent("{\"bookingId\":5,\"customerId\":42,\"status\":\"CONFIRMED\",\"totalAmount\":\"700.00\",\"customerEmail\":\"a@b.co\"}");

        NotificationRequest r = submitted();
        assertNull(r.getShowTitle());
        assertNull(r.getVenue());
        assertNull(r.getShowTime());
        assertTrue(r.getSeats().isEmpty());
    }

    @Test
    void seats_acceptACommaSeparatedStringToo_andIgnoreBlanksAndNulls() {
        handler.handleBookingEvent("{\"bookingId\":5,\"customerId\":1,\"status\":\"CONFIRMED\",\"customerEmail\":\"a@b.co\",\"seats\":\"A5, A6,, \"}");

        assertEquals(List.of("A5", "A6"), submitted().getSeats());
    }

    @Test
    void seats_ofAnUnexpectedShape_areIgnoredInsteadOfFailingTheEmail() {
        handler.handleBookingEvent("{\"bookingId\":5,\"customerId\":1,\"status\":\"CONFIRMED\",\"customerEmail\":\"a@b.co\",\"seats\":{\"x\":1}}");

        assertTrue(submitted().getSeats().isEmpty());
    }

    // ------------------------------------------------------------------ what must NOT send an email

    @Test
    void pendingBooking_sendsNothing() {
        handler.handleBookingEvent("{\"bookingId\":5,\"customerId\":42,\"status\":\"PENDING\",\"customerEmail\":\"a@b.co\"}");

        verify(notificationService, never()).submit(any());
    }

    @Test
    void confirmedEmailsSwitchedOff_sendsNothing() {
        when(notificationService.isEnabled(NotificationType.BOOKING_CONFIRMED)).thenReturn(false);

        handler.handleBookingEvent("{\"bookingId\":5,\"customerId\":42,\"status\":\"CONFIRMED\",\"customerEmail\":\"a@b.co\"}");

        verify(notificationService, never()).submit(any());
    }

    @Test
    void eventWithoutBookingId_isRejectedSoKafkaCanDeadLetterIt() {
        assertThrows(InvalidEventException.class,
                () -> handler.handleBookingEvent("{\"status\":\"CONFIRMED\",\"customerEmail\":\"a@b.co\"}"));
    }

    @Test
    void cancelledAndExpired_stillMapToTheirOwnEmails() {
        handler.handleBookingEvent("{\"bookingId\":6,\"customerId\":1,\"status\":\"CANCELLED\",\"customerEmail\":\"a@b.co\"}");
        handler.handleBookingEvent("{\"bookingId\":7,\"customerId\":1,\"status\":\"EXPIRED\",\"customerEmail\":\"a@b.co\"}");

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService, times(2)).submit(captor.capture());
        assertEquals(NotificationType.BOOKING_CANCELLED, captor.getAllValues().get(0).getType());
        assertEquals(NotificationType.BOOKING_EXPIRED, captor.getAllValues().get(1).getType());
    }

    // ------------------------------------------------------------------ payment events keep working

    @Test
    void paymentSuccess_whenItsEmailIsSwitchedOff_stillLearnsTheCustomersAddress() {
        when(notificationService.isEnabled(NotificationType.PAYMENT_SUCCESS)).thenReturn(false);

        handler.handlePaymentEvent("""
                {"eventType":"PAYMENT_SUCCESS","paymentId":11,"bookingId":5,"userId":7,"customerEmail":"asha@example.com",
                 "amount":700.00,"currency":"INR","status":"SUCCESS"}""");

        verify(contacts).remember(eq(7L), eq("asha@example.com"));
        verify(notificationService, never()).submit(any());
    }
}
