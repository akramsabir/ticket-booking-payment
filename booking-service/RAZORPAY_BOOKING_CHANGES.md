# Booking service - changes for the Razorpay integration

The full flow and the reasoning are in `payment-service/GUIDE.md`. This file lists what changed **here** and why.

## The behaviour change that matters
| Before | Now |
|---|---|
| `POST /bookings` sent `tok_success` to payment and the booking was **CONFIRMED immediately** | `POST /bookings` returns the booking **PENDING** (seats held for `booking.expiry.minutes`) plus `payment: { razorpayKeyId, razorpayOrderId, amountInMinorUnits, currency }`. The browser opens Razorpay Checkout with those values. |
| - | The booking becomes **CONFIRMED only when payment-service reports SUCCESS**: via `POST /bookings/{id}/payment/verify`, or via the reconcile job after a webhook-only payment. |

## API
| Endpoint | Purpose |
|---|---|
| `POST /bookings` | Now returns the Razorpay order details in `payment`. A retry with the same `Idempotency-Key` returns the same booking *and* the same order. |
| `POST /bookings/{id}/payment/verify` | Browser sends `razorpay_order_id`, `razorpay_payment_id`, `razorpay_signature` (snake or camel case). 200 `CONFIRMED` = paid; 200 `PENDING` = not captured yet; 400 = bad signature; 409 = booking already cancelled/expired (any money is refunded). |
| `GET /bookings/{id}/payment` | Order details / payment status (to re-open Checkout after a page reload). |
| `POST /bookings/{id}/cancel` | Same as `DELETE /bookings/{id}` (the blueprint lists the POST form). |

## Rules implemented (and the tests that guard them)
- **Never release the seats of a booking whose payment state is unknown.** The expiry job first asks payment-service to close the payment (which double-checks with Razorpay). If payment-service is down, the booking is *not* expired this round; the next scan retries. If the customer paid just before the deadline, the booking is confirmed instead of expired.
- **Cancel a confirmed booking = refund first.** If the refund fails the booking stays CONFIRMED (never "cancelled but still charged").
- **Cancel a pending booking** closes its payment first; if payment-service is unreachable the cancel fails with 503 rather than orphaning a later payment.
- **Payment service down at booking time** -> booking is cancelled and seats released, 503 returned (blueprint SC-16).
- **A 4xx from payment-service is an answer, not an outage**: passed through (`PaymentClientException`), never retried, not counted by the circuit breaker.
- **Late payment after expiry/cancel** is refunded; the booking is not revived (seats may already be sold again).
- A **reconcile job** (`booking.reconcile.scan-ms`, default 15 s) confirms bookings whose payment succeeded while the browser was gone (blueprint SC-06).

## Files
- New: `dto/external/PaymentRequest|PaymentResponse` (rewritten), `dto/request/VerifyPaymentRequest`, `dto/response/PaymentInfoResponse`, `exception/PaymentClientException`, `static/razorpay-checkout-test.html` (dev tool - delete before production).
- Changed: `client/PaymentClient`, `client/PaymentClientService`, `service/impl/BookingServiceImpl`, `service/BookingService`, `controller/BookingController`, `exception/GlobalExceptionHandler`, `repository/BookingRepository`, `dto/response/BookingResponse`, `application.yml` (payment-service read timeout 15 s, Resilience4j ignore-exceptions, reconcile interval).
- Tests: `BookingServiceImplTest` rewritten - the old one did not compile (it used a 6-argument `EventSeatDto` constructor that no longer exists). 30 scenarios.

## Not changed (noticed, out of scope for Razorpay)
- Customer email is still the placeholder `customer{id}@example.com` (marked with a TODO).
- `EventClientService.validateSeats` always reports the show as `SCHEDULED`; it never reads the real show status, so a cancelled show is still bookable. With real money this deserves a ticket.
- Seats are held in Booking's own `booking_seats` table; event-service's seat map still shows them `AVAILABLE` after booking (the original README already flags this).
