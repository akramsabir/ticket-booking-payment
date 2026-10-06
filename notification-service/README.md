# TicketHub – Notification Service

Sends **email** to TicketHub customers when something happens to their booking or payment.
Booking and Payment never wait for it (blueprint rule: *"Booking should not depend synchronously on notification delivery"*).

```
 payment-service ──Kafka "payment-events"──┐
                                           ├──▶  NOTIFICATION SERVICE ──SMTP──▶ customer's inbox
 booking-service ──Kafka "booking-events"──┘        │   ▲
                                                    ▼   │ retry job (automatic, with back-off)
 anything else ──REST /api/notifications/**──▶  own database (notifications table = delivery status)
```

## What it does

| Feature | Detail |
|---|---|
| **Email channel** | HTML + plain-text email through any SMTP server (Gmail, Mailtrap, SES SMTP, MailHog…) |
| **Kafka consumers** | `payment-events` (PAYMENT_SUCCESS / FAILED / REFUNDED) and `booking-events` (CONFIRMED / CANCELLED / EXPIRED) |
| **REST API** | send a free-text email to any address, trigger the ready-made emails, list/inspect/preview/retry |
| **Delivery record** | every notification is stored: `PENDING → SENT / FAILED`, attempts, last error, SMTP Message-Id (blueprint §5/§6) |
| **Idempotent** | same event delivered twice by Kafka ⇒ **one** email (unique `dedupe_key`; blueprint SC-08) |
| **Retry** | SMTP down ⇒ saved as `FAILED`, retried automatically 30 s, 60 s, 2 min … up to 5 attempts (blueprint SC-07) |
| **Dead-letter topics** | unreadable Kafka messages go to `payment-events.DLT` / `booking-events.DLT` instead of blocking the consumer (SC-09) |
| **Extensible channels** | `NotificationChannel` interface – SMS/push later = one new class |
| **Safe by default** | API-key protection (`X-API-Key`), header-injection-proof subjects, HTML-escaped templates, e-mail addresses masked in logs |

## Quick start (5 minutes, no real email needed)

**Prerequisites:** JDK 17+, Docker Desktop, MySQL (or use the `h2` profile – no MySQL needed).

```bash
# 1. Kafka + MailHog (fake inbox).  If payment-service's Kafka is already running, use:  docker compose up -d mailhog
docker compose up -d

# 2. Run the service (IntelliJ: run NotificationServiceApplication, or:)
./mvnw spring-boot:run                                   # MySQL: root/root @ localhost:3306 (same as booking-service)
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2     # …or without MySQL

# 3. Send yourself an email through the REST API
curl -X POST http://localhost:8085/api/notifications/email \
  -H "Content-Type: application/json" \
  -d '{"to":"anyone@example.org","subject":"Hello","message":"It works!"}'

# 4. Look at it: http://localhost:8025   (MailHog inbox)
```

Test the Kafka path too: `./scripts/send-sample-events.sh` publishes a payment event and a booking event → 2 more emails.
(Windows without Git Bash? Use the Postman collection in `postman/` or produce the JSON from `docs/INTEGRATION.md` manually.)

## Send REAL emails (Gmail example)

1. Google Account → Security → turn on 2-Step Verification → **App passwords** → create one (16 characters).
2. Set these environment variables (IntelliJ: Run → Edit Configurations → Environment variables):

```
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your.address@gmail.com
MAIL_PASSWORD=<the 16-character app password>
MAIL_SMTP_AUTH=true
MAIL_STARTTLS=true
MAIL_FROM=your.address@gmail.com        # Gmail only allows your own address as sender
```
3. Call `POST /api/notifications/email` with `"to"` = the end user's address. Done.

Other providers work the same way – only host/port/credentials change (Mailtrap, Brevo, SendGrid SMTP, Amazon SES SMTP…).
For production use a proper sender domain (SPF/DKIM) – Gmail is fine for development only.

## REST API  (base `http://localhost:8085`)

If `NOTIFICATION_API_KEY` is set, add header `X-API-Key: <key>` to every `/api/**` call.

| Method & path | Purpose |
|---|---|
| `POST /api/notifications/email` | Free-text email. Body: `to`, `subject`, `message`, optional `recipientName`, `idempotencyKey` (or header `Idempotency-Key`) |
| `POST /api/notifications/trigger` | Ready-made email. Body: `type` (`BOOKING_CONFIRMED`, `BOOKING_CANCELLED`, `BOOKING_EXPIRED`, `PAYMENT_SUCCESS`, `PAYMENT_FAILED`, `PAYMENT_REFUNDED`), `recipientEmail`, `bookingId`, optional `paymentId`, `amount`, `currency`, `reason`, `showTitle`, `venue`, `showTime`, `seats[]` |
| `GET /api/notifications` | List. Filters: `status`, `type`, `recipient`, `bookingId`, `page`, `size` |
| `GET /api/notifications/{id}` | One notification (status, attempts, lastError, nextRetryAt…) |
| `GET /api/notifications/{id}/preview` | The stored email as HTML (open in a browser) |
| `POST /api/notifications/{id}/retry` | Send again. Optional body `{"recipient":"fixed@address.com"}` |
| `GET /actuator/health` | Health |

Status codes of the two send endpoints: **201** sent · **202** saved, not delivered yet (auto-retry scheduled) · **200** duplicate request (nothing re-sent) · **400** validation · **409** that notification type is disabled / already sent.

## Kafka contracts (already match your services)

* **`payment-events`** – `PaymentEvent` record from payment-service: `eventType, paymentId, bookingId, userId, customerEmail, amount, currency, status, failureReason, occurredAt`. Dedupe key `PAYMENT:<eventType>:<paymentId>`.
* **`booking-events`** – JSON from booking-service's `BookingEventPublisher`: `bookingId, customerId, showId, status, totalAmount`, plus (when known) `customerEmail, showTitle, venue, showTime, seats` (e.g. `["A5","A6"]`). Dedupe key `BOOKING:<status>:<bookingId>`.
  The confirmation email is addressed to `customerEmail`. Older events without it fall back to the address remembered from payment events (table `user_contacts`). If no address is known the notification is saved as `FAILED` ("No recipient email address available") and you can fix it with the retry endpoint.
  `showTitle`, `venue`, `showTime` and `seats` are optional: they appear as rows in the email when present and are simply left out when not.

Messages are read as plain JSON text, so it does not matter which Java classes/type-headers the producers use.

## Configuration (`application.yml`, all overridable by env vars)

| Property / env var | Default | Meaning |
|---|---|---|
| `SERVER_PORT` | `8085` | HTTP port (8080 event, 8081 payment, 8082 user, 8083 booking, 8090 gateway, 8761 registry) |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | MySQL `notificationdb`, root/root | database (auto-created) |
| `MAIL_HOST` `MAIL_PORT` `MAIL_USERNAME` `MAIL_PASSWORD` `MAIL_SMTP_AUTH` `MAIL_STARTTLS` | MailHog `localhost:1025` | SMTP server |
| `MAIL_FROM`, `MAIL_FROM_NAME`, `MAIL_SUPPORT` | `no-reply@tickethub.local`, `TicketHub` | sender / support address in the footer |
| `MAIL_BLOCKED_DOMAINS` | empty | comma list, e.g. `example.com` – such recipients are rejected |
| `NOTIFICATION_API_KEY` | empty (open) | **set this outside your laptop** |
| `NOTIFICATION_KAFKA_ENABLED` | `true` | `false` = REST-only mode, no Kafka needed |
| `KAFKA_BOOTSTRAP_SERVERS`, `PAYMENT_TOPIC`, `BOOKING_TOPIC` | `localhost:9092`, `payment-events`, `booking-events` | Kafka |
| `notification.events.enabled.*` | all `true` | switch single email types on/off, e.g. `payment-success: false` |
| `notification.retry.*` | 5 attempts, 30 s start, ×2, max 30 min | retry policy |

**One email per successful booking (default).** The customer gets the *booking confirmation* (from `booking-events`), which already shows the amount paid.
The separate *payment receipt* (from `payment-events`) is switched **off** (`notification.events.enabled.payment-success: false`); set it to `true` if you want both.
Failed and refunded payments always send their own email.

## Project layout

```
src/main/java/com/tickethub/notification
├── channel/      NotificationChannel (interface), EmailChannel (SMTP), ChannelMessage, ChannelDeliveryException
├── config/       NotificationProperties, KafkaConsumerConfig (retry + dead-letter), ApiKeyFilter
├── controller/   NotificationController (REST)
├── dto/          request/response objects
├── entity/       NotificationRecord (notifications table), UserContact, enums
├── exception/    exceptions + GlobalExceptionHandler
├── kafka/        PaymentEventListener, BookingEventListener (thin – delegate to the handler)
├── repository/   Spring Data repositories (incl. the atomic "claim" used to avoid double sends)
└── service/      NotificationService (create/send/retry), NotificationRenderer, EventNotificationHandler,
                  NotificationRetryScheduler, UserContactService
src/main/resources/templates/email/notification.html   ← the email layout – edit this to restyle
```

* Change the **wording** of an email → `NotificationRenderer` (one `case` per type).
* Change the **look** → `templates/email/notification.html`.
* Add a **new event type** → add to `NotificationType`, add a `case` in the renderer, map it in `EventNotificationHandler`.

## Tests

`./mvnw test` – H2 in-memory DB, mocked SMTP, no Kafka needed. Covers: send + record, duplicate suppression, SMTP failure → scheduled retry → success, payment/booking event handling, unknown recipient + manual fix, malformed messages, HTML escaping, REST codes/validation, API-key protection.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `535 Username and Password not accepted` | Gmail needs an **app password**, not your normal password; `MAIL_FROM` must equal `MAIL_USERNAME` |
| Notification stays `FAILED`, `lastError` = "Could not connect to host" | SMTP host/port wrong or MailHog not running; it retries by itself, or call `/retry` |
| Port 8081 / 9092 already in use | user-service and payment-service both default to 8081 – change one; only run one Kafka |
| Kafka events arrive but no email | check `GET /api/notifications?status=FAILED`; booking events need a known email (see Kafka contracts) |
| Nothing in Kafka | `tickethub.kafka.enabled` (payment) / `booking.kafka.enabled` (booking) are `false` by default in your services – see `docs/INTEGRATION.md` |
| Want to see a stored email | `GET /api/notifications/{id}/preview` |

## Design notes / known limits

* No `@Transactional` around SMTP calls – a slow mail server never holds a DB transaction open.
* Retry uses a DB lease (`next_retry_at` pushed into the future via an atomic UPDATE) so two instances never send the same email.
  There is still the unavoidable *at-least-once* corner case: if the process dies in the instant after SMTP accepted the mail but before the row is updated, the retry sends it once more.
* Delivery to the provider (`SENT`) means *the SMTP server accepted the message*. Bounces/opens would need provider webhooks (blueprint phase-2 item).
* Uses `ddl-auto: update` like your other services. For production add Flyway migrations (as in ticket-hub).
