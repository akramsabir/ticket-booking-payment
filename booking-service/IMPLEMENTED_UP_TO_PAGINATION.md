# TicketHub Booking Service - implemented changes

This version extends the uploaded booking-service project with the following features:

1. Payment-service Feign client contract (disabled by default until payment-service is available).
2. PENDING -> CONFIRMED/CANCELLED payment-driven workflow when `booking.payment.enabled=true`.
3. Booking total amount calculated from event-service seat prices.
4. Price snapshot remains stored in `booking_seats`.
5. `@Version` optimistic locking on Booking.
6. Booking expiry after configurable minutes.
7. Expired bookings release local booking-seat rows.
8. Booking history table/entity/repository and `GET /bookings/{id}/history`.
9. Idempotency using `Idempotency-Key` header.
10. Optional JWT authentication filter. Disabled by default so existing Postman calls continue to work.
11. Kafka booking-event publisher. Disabled by default.
12. RateLimiter added to Event and Payment service clients.
13. Pagination and filtering on `GET /bookings`.
14. Database indexes for common booking queries.
15. Updated unit tests for the new service constructor and PENDING workflow.

## Important external contracts

### Payment service
When enabled, booking-service expects:

`POST /payments`

Request:
```json
{
  "bookingId": 1,
  "customerId": 1,
  "amount": 700.00,
  "idempotencyKey": "abc-123"
}
```

Response:
```json
{
  "paymentId": "PAY-1",
  "status": "SUCCESS",
  "message": "Payment successful"
}
```

Set:
```yaml
booking:
  payment:
    enabled: true
```
only after payment-service is running and registered with Eureka.

### JWT
Set:
```yaml
booking:
  security:
    enabled: true
    secret: <strong-32+-character-secret>
```
The filter accepts `Authorization: Bearer <JWT>`. It uses the `customerId` claim or JWT subject as the authenticated principal.

### Kafka
Set:
```yaml
booking:
  kafka:
    enabled: true
```
when Kafka is running. Topic defaults to `booking-events`.

## Pagination examples

```text
GET /bookings?page=0&size=20
GET /bookings?page=1&size=10
GET /bookings?page=0&size=20&customerId=1
GET /bookings?page=0&size=20&showId=100
GET /bookings?page=0&size=20&status=CONFIRMED
```

The maximum page size is 100 and results are sorted by `createdAt DESC`.

## Still external to this repository

The uploaded archive contains only booking-service. Therefore the actual Event Service implementation for an atomic `reserveSeats/releaseSeats` API cannot be changed here. The existing Booking DB unique constraint remains the final duplicate-seat safety net. Payment Service itself also must be implemented separately.
