package com.tickethub.booking.service;

import com.tickethub.booking.dto.request.CreateBookingRequest;
import com.tickethub.booking.dto.request.VerifyPaymentRequest;
import com.tickethub.booking.dto.response.PaymentInfoResponse;
import com.tickethub.booking.dto.response.BookingResponse;
import com.tickethub.booking.dto.response.BookingHistoryResponse;

import java.util.List;
import com.tickethub.booking.dto.response.PageResponse;
import com.tickethub.booking.enums.BookingStatus;

public interface BookingService {
    BookingResponse createBooking(CreateBookingRequest request, String idempotencyKey);
    BookingResponse getBookingById(Long id);
    PageResponse<BookingResponse> getBookings(int page, int size, Long customerId, Long showId, BookingStatus status);
    void cancelBooking(Long id);
    List<BookingHistoryResponse> getBookingHistory(Long id);
    void expirePendingBookings();

    /** Browser finished Razorpay Checkout: prove it with payment-service and, if the money is captured, confirm the booking. */
    BookingResponse verifyPayment(Long bookingId, VerifyPaymentRequest request);

    /** What the browser needs to (re)open Razorpay Checkout for a booking, or the payment's current status. */
    PaymentInfoResponse getPaymentInfo(Long bookingId);

    /** Safety net: confirm bookings whose payment succeeded but whose confirmation never reached us (webhook-only payments). */
    void reconcilePendingPayments();
}
