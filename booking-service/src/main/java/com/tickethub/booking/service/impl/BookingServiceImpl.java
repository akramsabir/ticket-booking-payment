package com.tickethub.booking.service.impl;

import com.tickethub.booking.client.EventClientService;
import com.tickethub.booking.client.PaymentClientService;
import com.tickethub.booking.constant.BookingConstants;
import com.tickethub.booking.dto.external.EventSeatDto;
import com.tickethub.booking.dto.external.PaymentRequest;
import com.tickethub.booking.dto.external.PaymentResponse;
import com.tickethub.booking.dto.external.ShowValidationDto;
import com.tickethub.booking.dto.request.CreateBookingRequest;
import com.tickethub.booking.dto.request.VerifyPaymentRequest;
import com.tickethub.booking.dto.response.BookingHistoryResponse;
import com.tickethub.booking.dto.response.BookingResponse;
import com.tickethub.booking.dto.response.PageResponse;
import com.tickethub.booking.dto.response.PaymentInfoResponse;
import com.tickethub.booking.entity.Booking;
import com.tickethub.booking.entity.BookingHistory;
import com.tickethub.booking.entity.BookingSeat;
import com.tickethub.booking.enums.BookingStatus;
import com.tickethub.booking.exception.IdempotencyConflictException;
import com.tickethub.booking.exception.InvalidRequestException;
import com.tickethub.booking.exception.InvalidShowException;
import com.tickethub.booking.exception.PaymentClientException;
import com.tickethub.booking.exception.PaymentFailedException;
import com.tickethub.booking.exception.ResourceNotFoundException;
import com.tickethub.booking.exception.SeatAlreadyBookedException;
import com.tickethub.booking.mapper.BookingMapper;
import com.tickethub.booking.repository.BookingHistoryRepository;
import com.tickethub.booking.repository.BookingRepository;
import com.tickethub.booking.repository.BookingSeatRepository;
import com.tickethub.booking.security.AuthenticatedUser;
import com.tickethub.booking.service.BookingEventPublisher;
import com.tickethub.booking.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Booking lifecycle with Razorpay payments.
 *
 *   createBooking  : validate seats -> save booking PENDING (seats held, expiry window) -> ask payment-service to open
 *                    a Razorpay order -> return the booking WITH the order details the browser needs for Checkout.
 *   (customer pays in Razorpay Checkout in the browser)
 *   verifyPayment  : browser brings the proof -> payment-service verifies it -> money captured? -> booking CONFIRMED.
 *   reconcile job  : payment succeeded but the browser never came back (closed tab)? payment-service learned it from the
 *                    webhook -> this job notices and confirms the booking.
 *   expire job     : window over and still unpaid -> close the payment, release the seats (EXPIRED).
 *   cancelBooking  : customer cancels -> unpaid: close the payment / paid: refund FIRST -> release the seats.
 *
 * Golden rules (blueprint section 7):
 *   - A booking becomes CONFIRMED only when payment-service says SUCCESS - never on a guess, never on the browser's word.
 *   - Never release the seats of a booking whose payment state we do not know.
 *   - Money problems must never be silent: if we cannot refund, the booking is NOT cancelled.
 *
 * TRANSACTIONS: slow remote calls happen OUTSIDE database transactions wherever possible; short TransactionTemplate
 * blocks do the local changes. (createBooking keeps its original method-level transaction.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final BookingHistoryRepository bookingHistoryRepository;
    private final BookingMapper bookingMapper;
    private final EventClientService eventClientService;
    private final PaymentClientService paymentClientService;
    private final BookingEventPublisher bookingEventPublisher;
    private final TransactionTemplate tx;

    @Value("${booking.payment.enabled:false}")
    private boolean paymentEnabled;

    @Value("${booking.expiry.minutes:10}")
    private long expiryMinutes;

    // =====================================================================================================
    // CREATE
    // =====================================================================================================

    // noRollbackFor: when payment cannot be started we CANCEL the booking (a database change) and then throw;
    // the cancellation must be saved, not rolled back with the exception.
    @Override
    @Transactional(noRollbackFor = {PaymentFailedException.class, PaymentClientException.class})
    public BookingResponse createBooking(CreateBookingRequest request, String idempotencyKey) {
        String key = (idempotencyKey == null || idempotencyKey.isBlank())
                ? UUID.randomUUID().toString() : idempotencyKey;

        var existing = bookingRepository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            // A key identifies ONE customer's request. If somebody else sends the same key we must not hand out
            // that other customer's booking and payment order - refuse instead.
            if (!Objects.equals(existing.get().getCustomerId(), request.getCustomerId())) {
                log.warn("Idempotency key reuse by a different customer (bookingId={})", existing.get().getId());
                throw new IdempotencyConflictException("This Idempotency-Key was already used for another request");
            }
            log.info("Returning existing booking for idempotencyKey={}", key);
            BookingResponse replay = bookingMapper.toResponse(existing.get());
            if (paymentEnabled && existing.get().getStatus() == BookingStatus.PENDING) {
                attachPaymentInfo(replay, existing.get().getId());   // a retrying client needs the order again
            }
            return replay;
        }

        log.info("Creating booking for customerId={}, showId={}, seats={}",
                request.getCustomerId(), request.getShowId(), request.getSeatIds());

        ShowValidationDto validation = eventClientService.validateSeats(request.getShowId(), request.getSeatIds());

        // Validate show status dynamically against all valid upstream active statuses
        if (validation == null
                || (!"ACTIVE".equalsIgnoreCase(validation.getShowStatus())
                && !"SCHEDULED".equalsIgnoreCase(validation.getShowStatus())
                && !"BOOKING_OPEN".equalsIgnoreCase(validation.getShowStatus())
                && !"OPEN".equalsIgnoreCase(validation.getShowStatus()))) {
            throw new InvalidShowException("Show " + request.getShowId() + " is not currently bookable");
        }

        Map<String, EventSeatDto> seatsByNumber = validation.getSeats().stream()
                .collect(Collectors.toMap(EventSeatDto::getSeatNumber, s -> s));

        List<String> invalidSeats = request.getSeatIds().stream()
                .filter(seatId -> {
                    EventSeatDto seat = seatsByNumber.get(seatId);
                    return seat == null || !seat.isValid();
                }).toList();

        if (!invalidSeats.isEmpty()) {
            throw new InvalidShowException("Seat(s) " + invalidSeats + " are not valid for show " + request.getShowId());
        }

        List<BookingSeat> alreadyTaken = bookingSeatRepository
                .findAllByShowIdAndSeatIdIn(request.getShowId(), request.getSeatIds());
        if (!alreadyTaken.isEmpty()) {
            String taken = alreadyTaken.stream().map(BookingSeat::getSeatId).collect(Collectors.joining(", "));
            throw new SeatAlreadyBookedException(
                    String.format(BookingConstants.SEAT_ALREADY_BOOKED, taken, request.getShowId()));
        }

        Booking booking = bookingMapper.toEntity(request);
        booking.setIdempotencyKey(key);
        booking.setExpiresAt(LocalDateTime.now().plusMinutes(expiryMinutes));

        // Where the confirmation email will go: the logged-in customer's address, kept on the booking so that a later
        // confirmation (by the reconcile job, or by an admin) still knows it. Empty only for calls without a login.
        AuthenticatedUser.current()
                .map(AuthenticatedUser::email)
                .filter(email -> email != null && !email.isBlank())
                .ifPresent(booking::setCustomerEmail);

        // What was bought, for the email: show title / venue / time (best effort: never blocks the booking)
        eventClientService.getShowSummary(request.getShowId()).ifPresent(show -> {
            booking.setShowTitle(show.title());
            booking.setVenueName(show.venue());
            booking.setShowTime(show.startTime());
        });

        // The amount is calculated HERE from event-service prices. The browser never sends a price (blueprint rule).
        BigDecimal total = BigDecimal.ZERO;
        for (BookingSeat seat : booking.getSeats()) {
            EventSeatDto eventSeat = seatsByNumber.get(seat.getSeatId());
            BigDecimal price = eventSeat.getPrice();
            seat.setPrice(price);
            seat.setSeatLabel(eventSeat.getLabel());
            total = total.add(price == null ? BigDecimal.ZERO : price);
        }
        booking.setTotalAmount(total);

        try {
            Booking saved = bookingRepository.saveAndFlush(booking);
            recordHistory(saved, null, BookingStatus.PENDING, "Booking created");

            PaymentResponse payment = null;
            if (paymentEnabled) {
                try {
                    payment = paymentClientService.createPayment(buildPaymentRequest(saved));
                } catch (PaymentFailedException | PaymentClientException e) {
                    // No payment order = the customer cannot pay = holding the seats helps nobody (SC-04 / SC-16)
                    cancelAndRelease(saved, "Payment could not be started: " + e.getMessage());
                    throw e;
                }
            }

            // The booking STAYS PENDING (seats held until the expiry time). It becomes CONFIRMED only after the
            // customer has paid and the payment is verified - see verifyPayment / reconcilePendingPayments.
            bookingEventPublisher.publish(saved);
            BookingResponse response = bookingMapper.toResponse(saved);
            if (payment != null) {
                response.setPayment(toInfo(payment));
            }
            return response;
        } catch (DataIntegrityViolationException ex) {
            log.warn("Concurrent booking/idempotency conflict for showId={}, seats={}",
                    request.getShowId(), request.getSeatIds());
            throw new SeatAlreadyBookedException(
                    String.format(BookingConstants.SEAT_ALREADY_BOOKED, request.getSeatIds(), request.getShowId()));
        }
    }

    private PaymentRequest buildPaymentRequest(Booking saved) {
        return PaymentRequest.builder()
                .bookingId(saved.getId())
                .userId(saved.getCustomerId())
                // The customer's real email (from their login, stored on the booking). The placeholder only exists for
                // calls that had no login (tests) - payment-service insists on a valid-looking address.
                .customerEmail(saved.getCustomerEmail() != null && !saved.getCustomerEmail().isBlank()
                        ? saved.getCustomerEmail()
                        : "customer" + saved.getCustomerId() + "@example.com")
                .amount(saved.getTotalAmount())
                .currency("INR")
                // One key per booking: if this call is retried (timeout, our Resilience4j retry) payment-service
                // returns the SAME order instead of opening a second one.
                .idempotencyKey("booking-" + saved.getId())
                .build();
    }

    // =====================================================================================================
    // VERIFY (browser finished paying)
    // =====================================================================================================

    @Override
    public BookingResponse verifyPayment(Long bookingId, VerifyPaymentRequest request) {
        requirePaymentsEnabled();
        BookingStatus status = statusOf(bookingId);   // 404 if unknown

        if (status == BookingStatus.CONFIRMED) {
            return responseOf(bookingId);             // the same proof posted twice: harmless
        }

        // payment-service checks the signature, asks Razorpay, and records the result. 400 = bad signature.
        PaymentResponse payment = paymentClientService.verifyPayment(request);

        if (!Objects.equals(payment.getBookingId(), bookingId)) {
            throw new IllegalStateException("This payment belongs to booking " + payment.getBookingId()
                    + ", not booking " + bookingId);
        }

        if (status != BookingStatus.PENDING) {
            // The reservation is gone (cancelled / expired) but money may have arrived. payment-service refunds such
            // late payments by itself; we make sure and tell the customer clearly.
            if (payment.isSuccessful()) {
                try {
                    paymentClientService.refundForBooking(bookingId);
                } catch (RuntimeException e) {
                    log.error("Booking {} is {} but a payment succeeded and the refund call failed: {}", bookingId, status, e.getMessage());
                }
            }
            throw new IllegalStateException("Booking " + bookingId + " is " + status
                    + ", so it cannot be confirmed. Any payment received will be refunded.");
        }

        if (payment.isSuccessful()) {
            confirmIfStillPending(bookingId, "Payment successful (paymentId=" + payment.getPaymentId() + ")");
            return responseOf(bookingId);
        }

        // Not captured (yet): still PENDING. The browser can retry; the reconcile job will pick up a late success.
        BookingResponse response = responseOf(bookingId);
        response.setPayment(toInfo(payment));
        return response;
    }

    @Override
    public PaymentInfoResponse getPaymentInfo(Long bookingId) {
        requirePaymentsEnabled();
        statusOf(bookingId);   // 404 if unknown
        List<PaymentResponse> payments = paymentClientService.getPaymentsForBooking(bookingId);
        return payments.stream().filter(PaymentResponse::isPayable).findFirst()
                .or(() -> payments.stream().findFirst())
                .map(this::toInfo)
                .orElseThrow(() -> new ResourceNotFoundException("No payment found for booking " + bookingId));
    }

    // =====================================================================================================
    // READS
    // =====================================================================================================

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getBookingById(Long id) {
        Booking booking = findBooking(id);
        return bookingMapper.toResponse(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> getBookings(int page, int size, Long customerId,
                                                     Long showId, BookingStatus status) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        PageRequest pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.DESC, BookingConstants.DEFAULT_SORT_FIELD));

        Page<Booking> result;
        if (customerId != null) {
            result = bookingRepository.findByCustomerId(customerId, pageable);
        } else if (showId != null) {
            result = bookingRepository.findByShowId(showId, pageable);
        } else if (status != null) {
            result = bookingRepository.findByStatus(status, pageable);
        } else {
            result = bookingRepository.findAll(pageable);
        }

        return PageResponse.<BookingResponse>builder()
                .content(result.getContent().stream().map(bookingMapper::toResponse).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingHistoryResponse> getBookingHistory(Long id) {
        if (!bookingRepository.existsById(id)) {
            throw new ResourceNotFoundException(String.format(BookingConstants.BOOKING_NOT_FOUND, id));
        }
        return bookingHistoryRepository.findByBookingIdOrderByChangedAtAsc(id).stream()
                .map(h -> BookingHistoryResponse.builder()
                        .id(h.getId())
                        .bookingId(h.getBookingId())
                        .oldStatus(h.getOldStatus())
                        .newStatus(h.getNewStatus())
                        .reason(h.getReason())
                        .changedBy(h.getChangedBy())
                        .changedAt(h.getChangedAt())
                        .build())
                .toList();
    }

    // =====================================================================================================
    // CANCEL
    // =====================================================================================================

    @Override
    public void cancelBooking(Long id) {
        BookingStatus status = statusOf(id);   // 404 if unknown
        if (status == BookingStatus.CANCELLED || status == BookingStatus.EXPIRED) {
            throw new IllegalStateException("Booking is already " + status);
        }

        // MONEY FIRST. If anything here fails, the exception stops the cancellation: the booking stays as it was and
        // the customer can simply try again (every payment-service call below is safe to repeat).
        if (paymentEnabled) {
            if (status == BookingStatus.CONFIRMED) {
                paymentClientService.refundForBooking(id);
            } else {
                List<PaymentResponse> after = paymentClientService.cancelPaymentsForBooking(id);
                if (after.stream().anyMatch(PaymentResponse::isSuccessful)) {
                    // The customer paid at this very moment: they are cancelling, so give it back.
                    paymentClientService.refundForBooking(id);
                }
            }
        }

        tx.executeWithoutResult(s -> {
            Booking booking = findBooking(id);
            if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.EXPIRED) {
                return;   // another request got there first
            }
            cancelAndRelease(booking, "Customer cancelled booking");
            bookingEventPublisher.publish(booking);
        });
    }

    // =====================================================================================================
    // BACKGROUND JOBS
    // =====================================================================================================

    @Override
    @Scheduled(fixedDelayString = "${booking.expiry.scan-ms:60000}")
    public void expirePendingBookings() {
        List<Long> due = tx.execute(s -> bookingRepository
                .findByStatusAndExpiresAtBefore(BookingStatus.PENDING, LocalDateTime.now())
                .stream().map(Booking::getId).toList());
        if (due == null || due.isEmpty()) {
            return;
        }
        int expired = 0;
        for (Long id : due) {
            try {
                if (expireOne(id)) {
                    expired++;
                }
            } catch (RuntimeException e) {
                // e.g. payment-service is down: we do NOT know whether this booking was paid, so we do NOT release
                // its seats. The next scan tries again.
                log.warn("Booking {} not expired this round: {}", id, e.getMessage());
            }
        }
        if (expired > 0) {
            log.info("Expired {} pending bookings", expired);
        }
    }

    private boolean expireOne(Long id) {
        if (paymentEnabled) {
            // Closes the unpaid payment - and double-checks with Razorpay that the customer did not pay a moment ago.
            List<PaymentResponse> after = paymentClientService.cancelPaymentsForBooking(id);
            if (after.stream().anyMatch(PaymentResponse::isSuccessful)) {
                confirmIfStillPending(id, "Payment found at expiry time; booking confirmed");
                return false;
            }
        }
        Boolean expired = tx.execute(s -> {
            Booking booking = findBooking(id);
            if (booking.getStatus() != BookingStatus.PENDING) {
                return false;
            }
            booking.setStatus(BookingStatus.EXPIRED);
            booking.setExpiresAt(null);
            int released = booking.getSeats().size();
            booking.getSeats().clear();
            bookingRepository.save(booking);
            recordHistory(booking, BookingStatus.PENDING, BookingStatus.EXPIRED,
                    "Payment window expired; seats released=" + released);
            bookingEventPublisher.publish(booking);
            return true;
        });
        return Boolean.TRUE.equals(expired);
    }

    /**
     * Every few seconds: for bookings still waiting inside their payment window, ask payment-service whether the
     * money has arrived. This is what confirms a booking when the customer paid but never came back to our
     * verify endpoint (closed the tab, lost network) and only Razorpay's webhook told payment-service.
     */
    @Override
    @Scheduled(fixedDelayString = "${booking.reconcile.scan-ms:15000}")
    public void reconcilePendingPayments() {
        if (!paymentEnabled) {
            return;
        }
        List<Long> waiting = tx.execute(s -> bookingRepository
                .findTop50ByStatusAndExpiresAtAfterOrderByCreatedAtAsc(BookingStatus.PENDING, LocalDateTime.now())
                .stream().map(Booking::getId).toList());
        if (waiting == null) {
            return;
        }
        for (Long id : waiting) {
            try {
                List<PaymentResponse> payments = paymentClientService.getPaymentsForBooking(id);
                if (payments.stream().anyMatch(PaymentResponse::isSuccessful)) {
                    confirmIfStillPending(id, "Payment successful (confirmed by reconciliation)");
                }
            } catch (RuntimeException e) {
                log.warn("Reconciliation of booking {} skipped: {}", id, e.getMessage());
            }
        }
    }

    // =====================================================================================================
    // helpers
    // =====================================================================================================

    /** PENDING -> CONFIRMED in a short transaction. Does nothing if the booking is no longer PENDING (seats may be released). */
    private void confirmIfStillPending(Long bookingId, String reason) {
        try {
            tx.executeWithoutResult(s -> {
                Booking booking = findBooking(bookingId);
                if (booking.getStatus() != BookingStatus.PENDING) {
                    log.info("Booking {} is {}, not confirming", bookingId, booking.getStatus());
                    return;
                }
                BookingStatus old = booking.getStatus();
                booking.setStatus(BookingStatus.CONFIRMED);
                booking.setExpiresAt(null);   // no longer expires
                bookingRepository.save(booking);
                recordHistory(booking, old, BookingStatus.CONFIRMED, reason);
                bookingEventPublisher.publish(booking);
                log.info("Booking {} CONFIRMED: {}", bookingId, reason);

                // --- ADDED: Synchronously notify event-service ---
                List<String> seatIds = booking.getSeats().stream()
                        .map(BookingSeat::getSeatId)
                        .toList();
                eventClientService.markSeatsAsBooked(booking.getShowId(), seatIds);
                // -------------------------------------------------
            });
        } catch (ObjectOptimisticLockingFailureException e) {
            log.info("Booking {} was changed concurrently while confirming; keeping the other change", bookingId);
        }
    }

    private void requirePaymentsEnabled() {
        if (!paymentEnabled) {
            throw new InvalidRequestException("Payments are not enabled (booking.payment.enabled=false)");
        }
    }

    private Booking findBooking(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(BookingConstants.BOOKING_NOT_FOUND, id)));
    }

    /** Reads only the status (no lazy collections), so it is safe outside a transaction. */
    private BookingStatus statusOf(Long id) {
        return tx.execute(s -> findBooking(id).getStatus());
    }

    private BookingResponse responseOf(Long id) {
        return tx.execute(s -> bookingMapper.toResponse(findBooking(id)));
    }

    /** Best effort: a response that lacks payment info is still correct; the client can call GET /bookings/{id}/payment. */
    private void attachPaymentInfo(BookingResponse response, Long bookingId) {
        try {
            List<PaymentResponse> payments = paymentClientService.getPaymentsForBooking(bookingId);
            payments.stream().filter(PaymentResponse::isPayable).findFirst()
                    .ifPresent(p -> response.setPayment(toInfo(p)));
        } catch (RuntimeException e) {
            log.warn("Could not attach payment info to booking {}: {}", bookingId, e.getMessage());
        }
    }

    private PaymentInfoResponse toInfo(PaymentResponse payment) {
        return PaymentInfoResponse.builder()
                .paymentId(payment.getPaymentId())
                .status(payment.getStatus())
                .razorpayKeyId(payment.getRazorpayKeyId())
                .razorpayOrderId(payment.getProviderOrderId())
                .amount(payment.getAmount())
                .amountInMinorUnits(payment.getAmountInMinorUnits())
                .currency(payment.getCurrency())
                .failureReason(payment.getFailureReason())
                .build();
    }

    private void cancelAndRelease(Booking booking, String reason) {
        BookingStatus old = booking.getStatus();
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setExpiresAt(null);
        int released = booking.getSeats().size();
        booking.getSeats().clear();
        bookingRepository.save(booking);
        recordHistory(booking, old, BookingStatus.CANCELLED, reason + "; seats released=" + released);
    }

    private void recordHistory(Booking booking, BookingStatus oldStatus,
                               BookingStatus newStatus, String reason) {
        bookingHistoryRepository.save(BookingHistory.builder()
                .bookingId(booking.getId())
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .reason(reason)
                .changedBy("system")
                .changedAt(LocalDateTime.now())
                .build());
    }


}
