package com.tickethub.booking.controller;

import com.tickethub.booking.dto.request.CreateBookingRequest;
import com.tickethub.booking.dto.request.VerifyPaymentRequest;
import com.tickethub.booking.dto.response.BookingHistoryResponse;
import com.tickethub.booking.dto.response.BookingResponse;
import com.tickethub.booking.dto.response.PageResponse;
import com.tickethub.booking.dto.response.PaymentInfoResponse;
import com.tickethub.booking.enums.BookingStatus;
import com.tickethub.booking.security.AuthenticatedUser;
import com.tickethub.booking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {
    private final BookingService bookingService;

    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {
        AuthenticatedUser user = requireUser(authentication);

        // If an explicit customerId was provided and does not match the token's userId, reject it
        if (request.getCustomerId() != null && !request.getCustomerId().equals(user.userId())) {
            throw new AccessDeniedException("customerId does not match the logged-in user");
        }
        // Bind the caller's verified userId from the JWT
        request.setCustomerId(user.userId());

        BookingResponse response = bookingService.createBooking(request, idempotencyKey);
        return ResponseEntity.created(URI.create("/bookings/" + response.getId())).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@bookingAccess.canAccess(#id, authentication)")
    public ResponseEntity<BookingResponse> getBooking(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.getBookingById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public ResponseEntity<PageResponse<BookingResponse>> getBookings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long showId,
            @RequestParam(required = false) BookingStatus status,
            Authentication authentication) {
        AuthenticatedUser user = requireUser(authentication);

        Long effectiveCustomerId = customerId;
        if (!user.isAdmin()) {
            if (customerId != null && !customerId.equals(user.userId())) {
                throw new AccessDeniedException("You can only list your own bookings");
            }
            effectiveCustomerId = user.userId();
        }
        return ResponseEntity.ok(bookingService.getBookings(page, size, effectiveCustomerId, showId, status));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("@bookingAccess.canAccess(#id, authentication)")
    public ResponseEntity<List<BookingHistoryResponse>> getBookingHistory(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.getBookingHistory(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@bookingAccess.canAccess(#id, authentication)")
    public ResponseEntity<Void> cancelBooking(@PathVariable Long id) {
        bookingService.cancelBooking(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@bookingAccess.canAccess(#id, authentication)")
    public ResponseEntity<Void> cancelBookingPost(@PathVariable Long id) {
        bookingService.cancelBooking(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/{id}/payment")
    @PreAuthorize("@bookingAccess.canAccess(#id, authentication)")
    public ResponseEntity<PaymentInfoResponse> getPaymentInfo(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.getPaymentInfo(id));
    }

    @PostMapping("/{id}/payment/verify")
    @PreAuthorize("@bookingAccess.canAccess(#id, authentication)")
    public ResponseEntity<BookingResponse> verifyPayment(@PathVariable Long id,
                                                         @Valid @RequestBody VerifyPaymentRequest request) {
        return ResponseEntity.ok(bookingService.verifyPayment(id, request));
    }

    private AuthenticatedUser requireUser(Authentication authentication) {
        return AuthenticatedUser.from(authentication)
                .orElseThrow(() -> new AccessDeniedException("No authenticated user"));
    }
}