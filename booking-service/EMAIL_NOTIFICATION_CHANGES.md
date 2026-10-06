# Booking service - what changed so the customer gets a "booking confirmed" email

The email itself is sent by notification-service. Booking's job is to publish a correct `booking-events` message
**after** a booking is confirmed. Before this change that did not work at all, for four reasons:

| Problem | Fix |
|---|---|
| Kafka was off by default, and had **no JSON serializer** (every send would have failed, logged only as "Unable to publish") | `application.yml`: JSON producer, `max.block.ms: 1000`, `booking.kafka.enabled` defaults to **true** (`BOOKING_KAFKA_ENABLED=false` to run without a broker) |
| The event was sent **before the database commit**: a rolled-back change could still email "confirmed" | `BookingEventPublisher` now sends in `afterCommit` (nothing is sent on rollback) |
| No recipient: the message had no email address | The login's email is stored on the booking (`bookings.customer_email`) when it is created, so later confirmations by the reconcile job or an admin still know it |
| Nothing to show in the email except a number | Show title / venue / time (`bookings.show_*`, `venue_name`) and seat labels like `A5` (`booking_seats.seat_label`) are frozen at booking time and sent in the event |

## The message (a contract with notification-service)
```json
{ "bookingId": 5, "customerId": 42, "showId": 100, "status": "CONFIRMED", "totalAmount": "700.00",
  "customerEmail": "ravi@example.com", "showTitle": "Inception", "venue": "PVR Phoenix",
  "showTime": "Sun 18 Oct 2026, 19:30", "seats": ["A5", "A6"] }
```
The last five fields are only present when known.

## Database
`ddl-auto: update` adds these nullable columns automatically: `bookings.customer_email`, `show_title`, `venue_name`, `show_time`,
and `booking_seats.seat_label`. Bookings made before this change have no email: their emails fall back to the address
notification-service learned from payment events, else they are recorded as FAILED there.

## Best-effort show details
The show title comes from one extra read-only call, `GET /api/v1/shows/{id}` on event-service, with a 3-second timeout and
**no effect on the booking if it fails** (the email just omits the show rows). If your event-service exposes the show at a
different path, change the one `@GetMapping` in `client/EventClient.getShowSummary`.

## Also changed
- The payment now uses the stored real email (was `customer{id}@example.com` unless a login was present).
- `spring.cloud.openfeign.client.config.event-service`: 3 s read timeout, so a slow event-service cannot hold up a booking.

## Tests
75 unit tests, including: the event is not sent until commit and never on rollback, the email is stored and survives to a
later confirmation, labels/times are formatted, and a failing show lookup does not stop a booking.
