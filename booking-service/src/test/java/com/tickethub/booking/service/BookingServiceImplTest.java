package com.tickethub.booking.service;

import com.tickethub.booking.client.EventClientService;
import com.tickethub.booking.client.PaymentClientService;
import com.tickethub.booking.dto.external.EventSeatDto;
import com.tickethub.booking.dto.external.PaymentRequest;
import com.tickethub.booking.dto.external.PaymentResponse;
import com.tickethub.booking.dto.external.ShowValidationDto;
import com.tickethub.booking.dto.request.CreateBookingRequest;
import com.tickethub.booking.dto.request.VerifyPaymentRequest;
import com.tickethub.booking.dto.response.BookingResponse;
import com.tickethub.booking.dto.response.PaymentInfoResponse;
import com.tickethub.booking.entity.Booking;
import com.tickethub.booking.entity.BookingHistory;
import com.tickethub.booking.entity.BookingSeat;
import com.tickethub.booking.enums.BookingStatus;
import com.tickethub.booking.exception.IdempotencyConflictException;
import com.tickethub.booking.exception.InvalidRequestException;
import com.tickethub.booking.exception.PaymentClientException;
import com.tickethub.booking.exception.PaymentFailedException;
import com.tickethub.booking.exception.SeatAlreadyBookedException;
import com.tickethub.booking.mapper.BookingMapper;
import com.tickethub.booking.repository.BookingHistoryRepository;
import com.tickethub.booking.repository.BookingRepository;
import com.tickethub.booking.repository.BookingSeatRepository;
import com.tickethub.booking.security.AuthenticatedUser;
import com.tickethub.booking.service.impl.BookingServiceImpl;
import com.tickethub.booking.support.TestTx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Booking x Razorpay scenarios (blueprint section 12): SC-01 happy path, SC-04 payment fails, SC-05/16 payment-service
 * down, SC-06 payment succeeded but confirmation lost, SC-07 expiry, SC-12 late payment.
 * Pure unit test: an in-memory bookings table, a mocked payment-service client, no Spring, no database.
 */
class BookingServiceImplTest {

    private BookingRepository bookingRepository;
    private BookingSeatRepository bookingSeatRepository;
    private BookingHistoryRepository historyRepository;
    private EventClientService eventClient;
    private PaymentClientService paymentClient;
    private BookingEventPublisher publisher;
    private BookingServiceImpl service;

    private final Map<Long, Booking> rows = new LinkedHashMap<>();
    private long sequence = 0;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        bookingSeatRepository = mock(BookingSeatRepository.class);
        historyRepository = mock(BookingHistoryRepository.class);
        eventClient = mock(EventClientService.class);
        paymentClient = mock(PaymentClientService.class);
        publisher = mock(BookingEventPublisher.class);

        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> store(inv.getArgument(0)));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> store(inv.getArgument(0)));
        when(bookingRepository.findById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return Optional.ofNullable(rows.get(id));
        });
        when(bookingRepository.findByIdempotencyKey(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return rows.values().stream().filter(b -> key.equals(b.getIdempotencyKey())).findFirst();
        });
        when(bookingRepository.findByStatusAndExpiresAtBefore(any(BookingStatus.class), any(LocalDateTime.class)))
                .thenAnswer(inv -> {
                    BookingStatus st = inv.getArgument(0);
                    LocalDateTime t = inv.getArgument(1);
                    return rows.values().stream()
                            .filter(b -> b.getStatus() == st && b.getExpiresAt() != null && b.getExpiresAt().isBefore(t)).toList();
                });
        when(bookingRepository.findTop50ByStatusAndExpiresAtAfterOrderByCreatedAtAsc(any(BookingStatus.class), any(LocalDateTime.class)))
                .thenAnswer(inv -> {
                    BookingStatus st = inv.getArgument(0);
                    LocalDateTime t = inv.getArgument(1);
                    return rows.values().stream()
                            .filter(b -> b.getStatus() == st && b.getExpiresAt() != null && b.getExpiresAt().isAfter(t)).toList();
                });

        service = new BookingServiceImpl(bookingRepository, bookingSeatRepository, historyRepository,
                new BookingMapper(), eventClient, paymentClient, publisher, TestTx.noOp());
        ReflectionTestUtils.setField(service, "paymentEnabled", true);
        ReflectionTestUtils.setField(service, "expiryMinutes", 10L);
    }

    @AfterEach
    void clearLogin() {
        SecurityContextHolder.clearContext();
    }

    private Booking store(Booking b) {
        if (b.getId() == null) {
            setId(b, ++sequence);
        }
        rows.put(b.getId(), b);
        return b;
    }

    private void setId(Booking b, long id) {
        b.setId(id);
    }

    // ---------------------------------------------------------------- fixtures

    private CreateBookingRequest bookingRequest() {
        return CreateBookingRequest.builder().customerId(1L).showId(100L).seatIds(List.of("11", "12")).build();
    }

    private void givenSeatsAvailable() {
        List<EventSeatDto> seats = List.of(
                EventSeatDto.builder().seatNumber("11").label("A5").price(new BigDecimal("350.00")).status("AVAILABLE").valid(true).build(),
                EventSeatDto.builder().seatNumber("12").label("A6").price(new BigDecimal("350.00")).status("AVAILABLE").valid(true).build());
        when(eventClient.validateSeats(anyLong(), any())).thenReturn(
                ShowValidationDto.builder().showId(100L).showStatus("SCHEDULED").seats(seats).build());
    }

    private PaymentResponse payment(String status, Long bookingId) {
        return PaymentResponse.builder().paymentId(55L).bookingId(bookingId).amount(new BigDecimal("700.00"))
                .amountInMinorUnits(70000).currency("INR").status(status).providerOrderId("order_abc")
                .razorpayKeyId("rzp_test_key").build();
    }

    /** A PENDING booking with two held seats, as createBooking would leave it. */
    private Booking pendingBooking() {
        Booking b = Booking.builder().customerId(1L).showId(100L).status(BookingStatus.PENDING)
                .totalAmount(new BigDecimal("700.00")).expiresAt(LocalDateTime.now().plusMinutes(5)).build();
        b.addSeat(BookingSeat.builder().showId(100L).seatId("11").price(new BigDecimal("350.00")).build());
        b.addSeat(BookingSeat.builder().showId(100L).seatId("12").price(new BigDecimal("350.00")).build());
        return store(b);
    }

    private Booking confirmedBooking() {
        Booking b = pendingBooking();
        b.setStatus(BookingStatus.CONFIRMED);
        b.setExpiresAt(null);
        return b;
    }

    private VerifyPaymentRequest proof() {
        return new VerifyPaymentRequest("order_abc", "pay_xyz", "sig");
    }

    private List<BookingStatus> historyNewStatuses() {
        ArgumentCaptor<BookingHistory> captor = ArgumentCaptor.forClass(BookingHistory.class);
        verify(historyRepository, atLeast(0)).save(captor.capture());
        return captor.getAllValues().stream().map(BookingHistory::getNewStatus).toList();
    }

    // ================================================================ CREATE

    @Test
    void create_paymentDisabled_staysPendingAndNeverCallsPaymentService() {
        ReflectionTestUtils.setField(service, "paymentEnabled", false);
        givenSeatsAvailable();

        BookingResponse r = service.createBooking(bookingRequest(), "key-1");

        assertEquals(BookingStatus.PENDING, r.getStatus());
        assertNull(r.getPayment());
        verifyNoInteractions(paymentClient);
    }

    @Test
    void create_paymentEnabled_returnsPendingBookingWithEverythingCheckoutNeeds() {
        givenSeatsAvailable();
        when(paymentClient.createPayment(any())).thenReturn(payment("PENDING", 1L));

        BookingResponse r = service.createBooking(bookingRequest(), "key-1");

        assertEquals(BookingStatus.PENDING, r.getStatus(), "NOT confirmed: the customer has not paid yet");
        assertEquals(new BigDecimal("700.00"), r.getTotalAmount());
        assertEquals(2, rows.get(r.getId()).getSeats().size(), "seats stay held while the customer pays");
        assertNotNull(rows.get(r.getId()).getExpiresAt());
        PaymentInfoResponse info = r.getPayment();
        assertEquals("order_abc", info.getRazorpayOrderId());
        assertEquals("rzp_test_key", info.getRazorpayKeyId());
        assertEquals(70000, info.getAmountInMinorUnits());
    }

    @Test
    void create_sendsPaymentServiceTheServerCalculatedAmountAndAStableIdempotencyKey() {
        givenSeatsAvailable();
        when(paymentClient.createPayment(any())).thenReturn(payment("PENDING", 1L));

        BookingResponse r = service.createBooking(bookingRequest(), "key-1");

        ArgumentCaptor<PaymentRequest> captor = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(paymentClient).createPayment(captor.capture());
        assertEquals(new BigDecimal("700.00"), captor.getValue().getAmount());   // 2 x 350 from event-service prices
        assertEquals("INR", captor.getValue().getCurrency());
        assertEquals("booking-" + r.getId(), captor.getValue().getIdempotencyKey());
    }

    @Test
    void create_paymentServiceDown_cancelsBookingAndReleasesSeats() {
        givenSeatsAvailable();
        when(paymentClient.createPayment(any())).thenThrow(new PaymentFailedException("down"));

        assertThrows(PaymentFailedException.class, () -> service.createBooking(bookingRequest(), "key-1"));

        Booking b = rows.get(1L);
        assertEquals(BookingStatus.CANCELLED, b.getStatus());
        assertTrue(b.getSeats().isEmpty(), "a customer who cannot pay must not block the seats");
    }

    @Test
    void create_paymentServiceRefuses_cancelsBookingAndReleasesSeats() {
        givenSeatsAvailable();
        when(paymentClient.createPayment(any())).thenThrow(new PaymentClientException(400, "bad amount"));

        assertThrows(PaymentClientException.class, () -> service.createBooking(bookingRequest(), "key-1"));

        assertEquals(BookingStatus.CANCELLED, rows.get(1L).getStatus());
        assertTrue(rows.get(1L).getSeats().isEmpty());
    }

    @Test
    void create_retryWithSameKey_returnsSameBookingWithItsPaymentOrderAgain() {
        givenSeatsAvailable();
        when(paymentClient.createPayment(any())).thenReturn(payment("PENDING", 1L));
        BookingResponse first = service.createBooking(bookingRequest(), "key-1");
        when(paymentClient.getPaymentsForBooking(first.getId())).thenReturn(List.of(payment("PENDING", first.getId())));

        BookingResponse retry = service.createBooking(bookingRequest(), "key-1");

        assertEquals(first.getId(), retry.getId());
        assertEquals(1, rows.size());
        assertEquals("order_abc", retry.getPayment().getRazorpayOrderId());
        verify(paymentClient, times(1)).createPayment(any());
    }

    @Test
    void create_seatAlreadyBooked_isRejectedBeforeAnyPaymentIsOpened() {
        givenSeatsAvailable();
        when(bookingSeatRepository.findAllByShowIdAndSeatIdIn(anyLong(), any()))
                .thenReturn(List.of(BookingSeat.builder().showId(100L).seatId("11").build()));

        assertThrows(SeatAlreadyBookedException.class, () -> service.createBooking(bookingRequest(), "key-1"));
        verifyNoInteractions(paymentClient);
    }


    // ================================================================ WHO IS BOOKING

    @Test
    void create_usesTheLoggedInUsersEmailForThePayment() {
        givenSeatsAvailable();
        when(paymentClient.createPayment(any())).thenReturn(payment("PENDING", 1L));
        AuthenticatedUser ravi = new AuthenticatedUser(1L, "ravi@example.com", Set.of("ROLE_CUSTOMER"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(ravi, null, List.of()));

        service.createBooking(bookingRequest(), "key-1");

        ArgumentCaptor<PaymentRequest> captor = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(paymentClient).createPayment(captor.capture());
        assertEquals("ravi@example.com", captor.getValue().getCustomerEmail());
    }

    @Test
    void create_withoutALogin_fallsBackToAPlaceholderEmail() {
        givenSeatsAvailable();
        when(paymentClient.createPayment(any())).thenReturn(payment("PENDING", 1L));

        service.createBooking(bookingRequest(), "key-1");

        ArgumentCaptor<PaymentRequest> captor = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(paymentClient).createPayment(captor.capture());
        assertEquals("customer1@example.com", captor.getValue().getCustomerEmail());
    }

    @Test
    void create_idempotencyKeyOfAnotherCustomer_isRefusedAndLeaksNothing() {
        givenSeatsAvailable();
        when(paymentClient.createPayment(any())).thenReturn(payment("PENDING", 1L));
        service.createBooking(bookingRequest(), "shared-key");          // customer 1 books with this key

        CreateBookingRequest otherCustomer = CreateBookingRequest.builder()
                .customerId(2L).showId(100L).seatIds(List.of("11", "12")).build();

        assertThrows(IdempotencyConflictException.class, () -> service.createBooking(otherCustomer, "shared-key"));
        verify(paymentClient, times(1)).createPayment(any());           // no order details handed to customer 2
        verify(paymentClient, never()).getPaymentsForBooking(anyLong());
    }


    // ================================================================ WHAT THE CONFIRMATION EMAIL NEEDS

    @Test
    void create_storesTheCustomersEmailOnTheBooking_soALaterConfirmationCanStillEmailThem() {
        givenSeatsAvailable();
        when(paymentClient.createPayment(any())).thenReturn(payment("PENDING", 1L));
        AuthenticatedUser ravi = new AuthenticatedUser(1L, "ravi@example.com", Set.of("ROLE_CUSTOMER"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(ravi, null, List.of()));

        BookingResponse r = service.createBooking(bookingRequest(), "key-1");
        SecurityContextHolder.clearContext();   // the later confirmation has no login at all (e.g. the reconcile job)

        assertEquals("ravi@example.com", rows.get(r.getId()).getCustomerEmail());
    }

    @Test
    void create_storesHumanSeatLabels() {
        givenSeatsAvailable();
        when(paymentClient.createPayment(any())).thenReturn(payment("PENDING", 1L));

        BookingResponse r = service.createBooking(bookingRequest(), "key-1");

        List<String> labels = rows.get(r.getId()).getSeats().stream().map(BookingSeat::getSeatLabel).toList();
        assertEquals(List.of("A5", "A6"), labels);
    }

    @Test
    void create_storesTheShowSnapshot() {
        givenSeatsAvailable();
        when(paymentClient.createPayment(any())).thenReturn(payment("PENDING", 1L));
        when(eventClient.getShowSummary(100L)).thenReturn(
                Optional.of(new EventClientService.ShowSummary("Inception", "PVR Phoenix", "Sun 18 Oct 2026, 19:30")));

        BookingResponse r = service.createBooking(bookingRequest(), "key-1");

        Booking stored = rows.get(r.getId());
        assertEquals("Inception", stored.getShowTitle());
        assertEquals("PVR Phoenix", stored.getVenueName());
        assertEquals("Sun 18 Oct 2026, 19:30", stored.getShowTime());
    }

    @Test
    void create_whenShowDetailsCannotBeRead_theBookingIsStillMade() {
        givenSeatsAvailable();
        when(paymentClient.createPayment(any())).thenReturn(payment("PENDING", 1L));
        when(eventClient.getShowSummary(100L)).thenReturn(Optional.empty());   // what the fail-soft client returns on any problem

        BookingResponse r = service.createBooking(bookingRequest(), "key-1");

        assertEquals(BookingStatus.PENDING, r.getStatus());
        assertNull(rows.get(r.getId()).getShowTitle());
    }

    @Test
    void create_paymentRequestUsesTheStoredEmail() {
        givenSeatsAvailable();
        when(paymentClient.createPayment(any())).thenReturn(payment("PENDING", 1L));
        AuthenticatedUser ravi = new AuthenticatedUser(1L, "ravi@example.com", Set.of("ROLE_CUSTOMER"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(ravi, null, List.of()));

        service.createBooking(bookingRequest(), "key-1");

        ArgumentCaptor<PaymentRequest> captor = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(paymentClient).createPayment(captor.capture());
        assertEquals("ravi@example.com", captor.getValue().getCustomerEmail());
    }

    // ================================================================ VERIFY

    @Test
    void verify_paymentSucceeded_confirmsTheBooking() {
        Booking b = pendingBooking();
        when(paymentClient.verifyPayment(any())).thenReturn(payment("SUCCESS", b.getId()));

        BookingResponse r = service.verifyPayment(b.getId(), proof());

        assertEquals(BookingStatus.CONFIRMED, r.getStatus());
        assertNull(rows.get(b.getId()).getExpiresAt(), "a confirmed booking must not expire");
        assertEquals(2, rows.get(b.getId()).getSeats().size());
        assertTrue(historyNewStatuses().contains(BookingStatus.CONFIRMED));
        verify(publisher).publish(rows.get(b.getId()));
    }

    @Test
    void verify_sameProofTwice_isHarmlessAndDoesNotCallPaymentServiceAgain() {
        Booking b = confirmedBooking();

        BookingResponse r = service.verifyPayment(b.getId(), proof());

        assertEquals(BookingStatus.CONFIRMED, r.getStatus());
        verifyNoInteractions(paymentClient);
    }

    @Test
    void verify_paymentNotCapturedYet_bookingStaysPendingAndSaysWhy() {
        Booking b = pendingBooking();
        when(paymentClient.verifyPayment(any())).thenReturn(payment("PENDING", b.getId()));

        BookingResponse r = service.verifyPayment(b.getId(), proof());

        assertEquals(BookingStatus.PENDING, r.getStatus());
        assertEquals("PENDING", r.getPayment().getStatus());
    }

    @Test
    void verify_forgedSignature_bookingIsNotConfirmed() {
        Booking b = pendingBooking();
        when(paymentClient.verifyPayment(any())).thenThrow(new PaymentClientException(400, "Invalid signature"));

        assertThrows(PaymentClientException.class, () -> service.verifyPayment(b.getId(), proof()));
        assertEquals(BookingStatus.PENDING, rows.get(b.getId()).getStatus());
    }

    @Test
    void verify_paymentBelongsToAnotherBooking_isRefused() {
        Booking b = pendingBooking();
        when(paymentClient.verifyPayment(any())).thenReturn(payment("SUCCESS", 999L));

        assertThrows(IllegalStateException.class, () -> service.verifyPayment(b.getId(), proof()));
        assertEquals(BookingStatus.PENDING, rows.get(b.getId()).getStatus());
    }

    @Test
    void verify_bookingAlreadyExpired_paymentIsRefundedAndBookingNotRevived() {
        // SC-12: the window closed, the seats went back on sale, then the slow payment lands
        Booking b = pendingBooking();
        b.setStatus(BookingStatus.EXPIRED);
        b.getSeats().clear();
        when(paymentClient.verifyPayment(any())).thenReturn(payment("SUCCESS", b.getId()));

        assertThrows(IllegalStateException.class, () -> service.verifyPayment(b.getId(), proof()));

        verify(paymentClient).refundForBooking(b.getId());
        assertEquals(BookingStatus.EXPIRED, rows.get(b.getId()).getStatus());
    }

    @Test
    void verify_paymentsDisabled_isRefused() {
        ReflectionTestUtils.setField(service, "paymentEnabled", false);
        assertThrows(InvalidRequestException.class, () -> service.verifyPayment(1L, proof()));
    }

    // ================================================================ PAYMENT INFO

    @Test
    void paymentInfo_prefersThePayableOrder() {
        Booking b = pendingBooking();
        when(paymentClient.getPaymentsForBooking(b.getId())).thenReturn(List.of(
                payment("CANCELLED", b.getId()), payment("PENDING", b.getId())));

        assertEquals("PENDING", service.getPaymentInfo(b.getId()).getStatus());
    }

    // ================================================================ CANCEL

    @Test
    void cancel_pendingBooking_closesThePaymentAndReleasesSeats() {
        Booking b = pendingBooking();
        when(paymentClient.cancelPaymentsForBooking(b.getId())).thenReturn(List.of(payment("CANCELLED", b.getId())));

        service.cancelBooking(b.getId());

        assertEquals(BookingStatus.CANCELLED, rows.get(b.getId()).getStatus());
        assertTrue(rows.get(b.getId()).getSeats().isEmpty());
        verify(paymentClient, never()).refundForBooking(anyLong());
    }

    @Test
    void cancel_pendingBooking_customerPaidAtThatMoment_getsRefunded() {
        Booking b = pendingBooking();
        when(paymentClient.cancelPaymentsForBooking(b.getId())).thenReturn(List.of(payment("SUCCESS", b.getId())));

        service.cancelBooking(b.getId());

        verify(paymentClient).refundForBooking(b.getId());
        assertEquals(BookingStatus.CANCELLED, rows.get(b.getId()).getStatus());
    }

    @Test
    void cancel_confirmedBooking_refundsFirstThenReleasesSeats() {
        Booking b = confirmedBooking();

        service.cancelBooking(b.getId());

        InOrder order = inOrder(paymentClient, publisher);
        order.verify(paymentClient).refundForBooking(b.getId());
        order.verify(publisher).publish(rows.get(b.getId()));
        assertEquals(BookingStatus.CANCELLED, rows.get(b.getId()).getStatus());
    }

    @Test
    void cancel_confirmedBooking_refundFails_bookingStaysConfirmed() {
        Booking b = confirmedBooking();
        when(paymentClient.refundForBooking(b.getId())).thenThrow(new PaymentFailedException("down"));

        assertThrows(PaymentFailedException.class, () -> service.cancelBooking(b.getId()));

        assertEquals(BookingStatus.CONFIRMED, rows.get(b.getId()).getStatus());
        assertEquals(2, rows.get(b.getId()).getSeats().size());
    }

    @Test
    void cancel_pendingBooking_paymentServiceDown_keepsBookingSoNoLateMoneyIsOrphaned() {
        Booking b = pendingBooking();
        when(paymentClient.cancelPaymentsForBooking(b.getId())).thenThrow(new PaymentFailedException("down"));

        assertThrows(PaymentFailedException.class, () -> service.cancelBooking(b.getId()));

        assertEquals(BookingStatus.PENDING, rows.get(b.getId()).getStatus());
    }

    @Test
    void cancel_alreadyCancelled_isRefused() {
        Booking b = pendingBooking();
        b.setStatus(BookingStatus.CANCELLED);

        assertThrows(IllegalStateException.class, () -> service.cancelBooking(b.getId()));
    }

    // ================================================================ EXPIRY

    private Booking overdueBooking() {
        Booking b = pendingBooking();
        b.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        return b;
    }

    @Test
    void expiry_unpaidBooking_isExpiredAndSeatsAreReleased() {
        Booking b = overdueBooking();
        when(paymentClient.cancelPaymentsForBooking(b.getId())).thenReturn(List.of(payment("CANCELLED", b.getId())));

        service.expirePendingBookings();

        assertEquals(BookingStatus.EXPIRED, rows.get(b.getId()).getStatus());
        assertTrue(rows.get(b.getId()).getSeats().isEmpty());
    }

    @Test
    void expiry_customerPaidJustBeforeTheDeadline_isConfirmedNotExpired() {
        Booking b = overdueBooking();
        when(paymentClient.cancelPaymentsForBooking(b.getId())).thenReturn(List.of(payment("SUCCESS", b.getId())));

        service.expirePendingBookings();

        assertEquals(BookingStatus.CONFIRMED, rows.get(b.getId()).getStatus());
        assertEquals(2, rows.get(b.getId()).getSeats().size());
    }

    @Test
    void expiry_paymentServiceDown_doesNotReleaseSeatsOfABookingWhosePaymentIsUnknown() {
        Booking b = overdueBooking();
        when(paymentClient.cancelPaymentsForBooking(b.getId())).thenThrow(new PaymentFailedException("down"));

        service.expirePendingBookings();   // must not throw

        assertEquals(BookingStatus.PENDING, rows.get(b.getId()).getStatus());
        assertEquals(2, rows.get(b.getId()).getSeats().size());
    }

    @Test
    void expiry_oneFailureDoesNotStopTheOthers() {
        Booking bad = overdueBooking();
        Booking good = overdueBooking();
        when(paymentClient.cancelPaymentsForBooking(bad.getId())).thenThrow(new PaymentFailedException("down"));
        when(paymentClient.cancelPaymentsForBooking(good.getId())).thenReturn(new ArrayList<>());

        service.expirePendingBookings();

        assertEquals(BookingStatus.PENDING, rows.get(bad.getId()).getStatus());
        assertEquals(BookingStatus.EXPIRED, rows.get(good.getId()).getStatus());
    }

    @Test
    void expiry_paymentDisabled_expiresWithoutTalkingToPaymentService() {
        ReflectionTestUtils.setField(service, "paymentEnabled", false);
        Booking b = overdueBooking();

        service.expirePendingBookings();

        assertEquals(BookingStatus.EXPIRED, rows.get(b.getId()).getStatus());
        verifyNoInteractions(paymentClient);
    }

    // ================================================================ RECONCILIATION (SC-06)

    @Test
    void reconcile_paymentSucceededButBrowserNeverCameBack_bookingGetsConfirmed() {
        Booking b = pendingBooking();
        when(paymentClient.getPaymentsForBooking(b.getId())).thenReturn(List.of(payment("SUCCESS", b.getId())));

        service.reconcilePendingPayments();

        assertEquals(BookingStatus.CONFIRMED, rows.get(b.getId()).getStatus());
        assertNull(rows.get(b.getId()).getExpiresAt());
    }

    @Test
    void reconcile_stillUnpaid_changesNothing() {
        Booking b = pendingBooking();
        when(paymentClient.getPaymentsForBooking(b.getId())).thenReturn(List.of(payment("PENDING", b.getId())));

        service.reconcilePendingPayments();

        assertEquals(BookingStatus.PENDING, rows.get(b.getId()).getStatus());
    }

    @Test
    void reconcile_paymentServiceDown_isSilentlySkipped() {
        Booking b = pendingBooking();
        when(paymentClient.getPaymentsForBooking(anyLong())).thenThrow(new PaymentFailedException("down"));

        service.reconcilePendingPayments();   // must not throw

        assertEquals(BookingStatus.PENDING, rows.get(b.getId()).getStatus());
    }

    @Test
    void reconcile_doesNothingWhenPaymentsAreDisabled() {
        ReflectionTestUtils.setField(service, "paymentEnabled", false);
        pendingBooking();

        service.reconcilePendingPayments();

        verifyNoInteractions(paymentClient);
    }
}
