package com.tickethub.booking.service;

import com.tickethub.booking.entity.Booking;
import com.tickethub.booking.entity.BookingSeat;
import com.tickethub.booking.enums.BookingStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The booking-events message is what turns into a customer's email, so two things must hold:
 * it carries what the email needs, and it is NEVER sent for a change that was rolled back.
 */
@SuppressWarnings("unchecked")
class BookingEventPublisherTest {

    private KafkaTemplate<String, Object> kafka;
    private BookingEventPublisher publisher;

    @BeforeEach
    void setUp() {
        kafka = mock(KafkaTemplate.class);
        publisher = new BookingEventPublisher(kafka);
        ReflectionTestUtils.setField(publisher, "enabled", true);
        ReflectionTestUtils.setField(publisher, "topic", "booking-events");
    }

    @AfterEach
    void cleanUp() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private Booking confirmedBooking() {
        Booking b = Booking.builder().id(5L).customerId(42L).showId(100L).status(BookingStatus.CONFIRMED)
                .totalAmount(new BigDecimal("700.00"))
                .customerEmail("ravi@example.com").showTitle("Inception").venueName("PVR Phoenix").showTime("Sat 18 Oct 2026, 19:30")
                .build();
        b.addSeat(BookingSeat.builder().showId(100L).seatId("11").seatLabel("A5").build());
        b.addSeat(BookingSeat.builder().showId(100L).seatId("12").seatLabel("A6").build());
        return b;
    }

    // ------------------------------------------------------------------ content

    @Test
    void message_carriesEverythingTheConfirmationEmailNeeds() {
        Map<String, Object> event = publisher.toEvent(confirmedBooking());

        assertEquals(5L, event.get("bookingId"));
        assertEquals(42L, event.get("customerId"));
        assertEquals("CONFIRMED", event.get("status"));
        assertEquals("700.00", event.get("totalAmount"));
        assertEquals("ravi@example.com", event.get("customerEmail"));
        assertEquals("Inception", event.get("showTitle"));
        assertEquals("PVR Phoenix", event.get("venue"));
        assertEquals("Sat 18 Oct 2026, 19:30", event.get("showTime"));
        assertEquals(List.of("A5", "A6"), event.get("seats"));
    }

    @Test
    void message_withMissingDetails_stillBuilds_andOmitsThem() {
        Booking bare = Booking.builder().id(6L).customerId(1L).showId(2L).status(BookingStatus.CANCELLED).build();

        Map<String, Object> event = publisher.toEvent(bare);   // used to be a NullPointerException risk with Map.of

        assertEquals("0", event.get("totalAmount"));
        assertFalse(event.containsKey("customerEmail"));
        assertFalse(event.containsKey("showTitle"));
        assertFalse(event.containsKey("seats"), "released seats of a cancelled booking are not listed");
    }

    @Test
    void seatsWithoutALabel_fallBackToTheSeatId() {
        Booking b = confirmedBooking();
        b.getSeats().get(0).setSeatLabel(null);

        assertEquals(List.of("11", "A6"), publisher.toEvent(b).get("seats"));
    }

    // ------------------------------------------------------------------ when it is sent

    @Test
    void withoutATransaction_isSentImmediately() {
        publisher.publish(confirmedBooking());

        verify(kafka).send(eq("booking-events"), eq("5"), any());
    }

    @Test
    void insideATransaction_isNotSentUntilTheCommit() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.publish(confirmedBooking());

        verify(kafka, never()).send(anyString(), anyString(), any());   // still inside the transaction: nothing yet

        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
        verify(kafka, times(1)).send(eq("booking-events"), eq("5"), any());
    }

    @Test
    void ifTheTransactionRollsBack_nothingIsEverSent() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.publish(confirmedBooking());
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);   // rollback: afterCommit is never called
        }

        verifyNoMoreInteractions(kafka);
    }

    @Test
    void whenDisabled_nothingIsSent() {
        ReflectionTestUtils.setField(publisher, "enabled", false);

        publisher.publish(confirmedBooking());

        verifyNoMoreInteractions(kafka);
    }

    @Test
    void kafkaFailure_isSwallowed_soTheBookingStillSucceeds() {
        when(kafka.send(anyString(), anyString(), any())).thenThrow(new IllegalStateException("broker down"));

        assertDoesNotThrow(() -> publisher.publish(confirmedBooking()));
    }
}
