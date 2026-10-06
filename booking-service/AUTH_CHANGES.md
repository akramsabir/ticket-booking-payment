# Booking service - authentication & authorization

## The rule
Only a **logged-in** user (valid JWT from user-service) with the **CUSTOMER** role can book a ticket, and the booking is **always made for that user**. Customers can only see, pay for or cancel **their own** bookings; **ADMIN** can see and cancel any.

| Situation | Result |
|---|---|
| No token / bad token / expired token | **401** |
| Valid token, but role is not CUSTOMER (e.g. admin only) tries to book | **403** |
| Customer opens / cancels / pays / lists someone else's booking | **403** (a missing booking id also gives 403, so ids cannot be probed) |
| Body contains a `customerId` that is not the logged-in user's | **403** |
| Customer lists bookings | always filtered to their own id; `?customerId=<other>` gives 403 |
| ADMIN | any booking, any `customerId` filter |

## What changed
| File | Change |
|---|---|
| `security/JwtAuthenticationFilter` | Rewritten. Validates the user-service token (same secret), reads `userId` + `roles`. Old version expected claims user-service never sends. |
| `security/AuthenticatedUser` (new) | "Who is calling": userId, email, roles, built from the token. |
| `security/SecurityConfig` | Rewritten. Security is **always on** (the `booking.security.enabled` switch is gone). JSON 401/403 in the service's normal error format. Method security enabled. |
| `security/BookingAccess` (new) | The ownership rule: owner or ADMIN. |
| `controller/BookingController` | `@PreAuthorize` on every endpoint; customer taken from the token; list scoped to the caller. |
| `dto/request/CreateBookingRequest` | `customerId` is optional and not trusted. |
| `service/impl/BookingServiceImpl` | (1) An `Idempotency-Key` can only be replayed by the same customer (before, anyone sending the key got that customer's booking and payment order). (2) The payment now carries the logged-in user's real email instead of `customer{id}@example.com`. Your `markSeatsAsBooked` change is untouched. |
| `exception/GlobalExceptionHandler` | `AccessDeniedException` -> 403 (otherwise the catch-all turns it into a 500), `IdempotencyConflictException` -> 409. |
| `config/AuditingConfig` | `createdBy` = user id (or `system` for background jobs). |
| `application.yml` | `booking.security.jwt-secret` replaces `enabled` / `secret`. |
| `static/razorpay-checkout-test.html` | Token box; no customerId any more. |

## Setup
1. **Same secret in both services.** user-service: `app.jwt.secret` (env `JWT_SECRET`). booking-service: `booking.security.jwt-secret` (same env var `JWT_SECRET`). The local defaults already match, so nothing to do on your laptop. For anything real set `JWT_SECRET` (32+ characters) on **both**.
2. **Ports:** event 8080, payment 8081, **user 8082 (changed from 8081, which payment uses)**, booking 8083.
3. Restart user-service **and** log in again: old tokens have no `userId` and are rejected (401).

## Try it
```bash
# 1. a customer
curl -X POST localhost:8082/api/auth/register -H "Content-Type: application/json" \
  -d '{"firstName":"Ravi","lastName":"Kumar","email":"ravi@example.com","password":"Secret@123"}'
TOKEN=$(curl -s -X POST localhost:8082/api/auth/login -H "Content-Type: application/json" \
  -d '{"email":"ravi@example.com","password":"Secret@123"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")

# 2. no token -> 401
curl -i -X POST localhost:8083/bookings -H "Content-Type: application/json" -d '{"showId":1,"seatIds":["1"]}'

# 3. with token -> booking for Ravi (no customerId needed)
curl -i -X POST localhost:8083/bookings -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: t-1" \
  -H "Content-Type: application/json" -d '{"showId":1,"seatIds":["1"]}'

# 4. pretend to be somebody else -> 403
curl -i -X POST localhost:8083/bookings -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"customerId":999,"showId":1,"seatIds":["2"]}'

# 5. register a SECOND customer, then use THEIR token on Ravi's booking -> 403
curl -i localhost:8083/bookings/1 -H "Authorization: Bearer $OTHER_TOKEN"

# 6. admin (admin@tickethub.com / Admin@12345 is seeded by user-service) -> can read any booking, but cannot book
```
Or open `http://localhost:8083/razorpay-checkout-test.html`, paste the token, and run the payment flow as before.

## Known limits (be aware)
- **Blocked users / changed roles take effect when their token expires (<= 1 hour).** user-service re-checks the database on every request; booking only trusts the signed token. If you need instant revocation, booking must call user-service per request, or you use shorter tokens plus refresh tokens.
- **ADMIN cannot book** unless the account also has the CUSTOMER role (`PUT /api/admin/users/{id}/roles` with both). Change `hasRole('CUSTOMER')` on `createBooking` if you want otherwise.
- **event-service and payment-service are still unauthenticated**, as before. Keep them off the public network (only booking, and Razorpay's webhook for payment, should reach them).
- Swagger UI and the checkout test page are public; delete the test page before production.
- If the shared secret leaks, anyone can mint tokens. Use a long random `JWT_SECRET`, never commit it.
