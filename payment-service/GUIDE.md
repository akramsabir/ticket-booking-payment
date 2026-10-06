# TicketHub Payment Service - Razorpay Integration Guide

Read this top to bottom once. The code carries comments explaining *why* each decision was made, so you can read the guide and the code side by side.

---

## 1. What changed, in one picture

The old version "charged a token" and answered SUCCESS immediately. **Razorpay does not work that way**: the customer pays inside Razorpay's own popup in the browser, so our server never sees card data. The flow has four steps:

```
 Browser            Booking service            Payment service                Razorpay
    |  POST /bookings      |                          |                           |
    |--------------------->|  POST /api/v1/payments   |                           |
    |                      |------------------------->|  create ORDER (amount) -->|
    |                      |                          |<-- order_xxx -------------|
    |<-- booking PENDING + { keyId, orderId, amount } |                           |
    |                                                                             |
    |  open Razorpay Checkout(keyId, orderId)  ... customer pays ................>|
    |<-- { razorpay_order_id, razorpay_payment_id, razorpay_signature } ----------|
    |                                                                             |
    |  POST /bookings/{id}/payment/verify               |                         |
    |--------------------->|  POST /api/v1/payments/verify                        |
    |                      |------------------------->|  1. check signature       |
    |                      |                          |  2. fetch payment ------->|
    |                      |                          |<-- captured, amount ------|
    |                      |<-- SUCCESS --------------|  3. save, publish Kafka   |
    |<-- booking CONFIRMED |                          |                           |
    |                                                                             |
    |        (in parallel, even if the tab was closed)                            |
    |                                       POST /api/v1/payments/webhook <-------|
```

Three independent paths can tell us a payment succeeded - **browser verify**, **webhook**, and a **background sync job** - and all three go through one state machine under a database row lock, so they can never double-process a payment.

### Why each design decision was made

| Decision | Reason |
|---|---|
| Amount is set when we create the **order**, by Booking, from event-service prices | The browser can never change what it pays (blueprint: "amount must be derived from the booking"). |
| A valid signature is **not enough**; we also fetch the payment from Razorpay and compare order id, amount, currency | Defence in depth. A signature proves Razorpay signed *something*; the fetch proves it is *this* order for *this* amount. |
| Only `captured` means SUCCESS (not `authorized`) | Authorized money can still be released by the bank. |
| `FAILED` is **not final** | One Razorpay order allows several attempts (card declined, then UPI works). A later `captured` must still win. |
| A `failed` event arriving **after** SUCCESS is ignored | Webhooks arrive out of order (blueprint SC-11). |
| Webhook receipt is saved in the **same DB transaction** as the work | A half-processed event gets retried by Razorpay; a finished one is ignored the second time (SC-10). |
| Money captured for a **CANCELLED** payment is refunded automatically | The booking expired but the slow UPI payment landed anyway (SC-12). |
| No DB transaction is held while waiting for Razorpay | Razorpay calls can take seconds; we use short `TransactionTemplate` blocks with `SELECT ... FOR UPDATE`. |
| Kafka events are published **after** commit | A rolled-back change must never produce an email. |

---

## 2. One-time setup

### 2.1 Razorpay account and keys
1. Create an account at https://dashboard.razorpay.com and stay in **Test Mode** (toggle at the top). Test mode uses fake money.
2. **Settings > API Keys > Generate Test Key.** You get a *Key Id* (`rzp_test_...`) and a *Key Secret* (shown once - copy it now).
3. **Settings > Webhooks > Add New Webhook** (see section 5). You choose a *Secret* yourself. It is a **different** secret from the API Key Secret.

### 2.2 Environment variables (never put real keys in a file that goes to Git)
```bash
export RAZORPAY_KEY_ID=rzp_test_xxxxxxxx
export RAZORPAY_KEY_SECRET=xxxxxxxxxxxxxxxx
export RAZORPAY_WEBHOOK_SECRET=the-secret-you-typed-in-the-dashboard
```
In IntelliJ: *Run > Edit Configurations > Environment variables*. If a variable is missing the app **refuses to start** and tells you which one - on purpose, so you never find out on a customer's first payment.

No Razorpay account yet? Run with `PAYMENT_GATEWAY=mock` (section 4.2).

### 2.3 Database
Same MySQL as before. `ddl-auto: update` adds the new columns to `payments` and creates `payment_webhook_events` automatically on first start. Nothing to run by hand. (The old `provider_transaction_id` column is simply left unused.)

---

## 3. What is in the code

```
config/      PaymentProperties   all settings in one place (env vars -> Java)
             PaymentConfig       builds the RazorpayClient, fails fast on missing keys, enables the sync job
gateway/     PaymentGateway      the "door" to the provider (interface)
             RazorpayPaymentGateway   the only class that knows Razorpay's SDK
             MockPaymentGateway       fake provider, same flow, no keys
             HmacSignatures      HMAC-SHA256 + constant-time compare
             AmountUtil          Rs 500.00 <-> 50000 paise, never rounds silently
entity/      Payment (+ order/payment/refund ids), PaymentStatus, PaymentMethod, WebhookEvent
repository/  PaymentRepository (incl. row-locking query), WebhookEventRepository
service/     PaymentService         create order, verify, refund, cancel, sync, THE state machine
             PaymentWebhookService  signature + dedupe + dispatch of Razorpay's calls
             PaymentSyncJob         every minute asks Razorpay about payments we never heard back on
controller/  PaymentController          endpoints for Booking
             PaymentWebhookController   the one public endpoint, for Razorpay
```

### Endpoints

| Method + path | Who calls it | Purpose |
|---|---|---|
| `POST /api/v1/payments` | Booking | Create a Razorpay order. Same `idempotencyKey` (or an already-open order for the booking) returns the **same** order. |
| `POST /api/v1/payments/verify` | Booking (forwarding the browser's proof) | Verify signature + fetch from Razorpay -> `SUCCESS`. |
| `GET /api/v1/payments/{id}` / `.../booking/{bookingId}` | Booking / you | Read payments. |
| `POST /api/v1/payments/{id}/sync` | You / ops | Ask Razorpay what really happened and update our record. |
| `POST /api/v1/payments/{id}/refund`, `.../booking/{bookingId}/refund` | Booking / ops | Refund. Repeating is harmless. |
| `POST /api/v1/payments/booking/{bookingId}/cancel` | Booking | Booking cancelled/expired: close unpaid payments (if the customer just paid, it is returned as `SUCCESS`, not cancelled). |
| `POST /api/v1/payments/webhook` | **Razorpay only** | Webhooks. The signature is the authentication. |

> **Security note:** only `/webhook` must be reachable from the internet. Keep every other endpoint internal (behind your gateway / private network): `POST /api/v1/payments` trusts the amount it is given, because only Booking is supposed to call it.

### Payment states
`INITIATED -> PENDING -> SUCCESS -> REFUNDED`, with `FAILED` (latest attempt failed, may still be retried) and `CANCELLED` (we gave up on the order). See the diagram in `PaymentStatus.java`.

---

## 4. Run and try it

### 4.1 Tests (no Razorpay, database or Kafka needed)
```bash
mvn test
```

### 4.2 Without Razorpay (mock provider)
```bash
PAYMENT_GATEWAY=mock mvn spring-boot:run
```
```bash
# 1. create an order
curl -s -X POST localhost:8081/api/v1/payments -H 'Content-Type: application/json' -d '{
  "bookingId": 101, "userId": 7, "customerEmail": "asha@example.com",
  "amount": 500.00, "currency": "INR", "idempotencyKey": "booking-101" }'
# -> note providerOrderId, e.g. order_mock_ab12cd34ef56  (status PENDING)

# 2. "pay": mock payment id = order id with order_ replaced by pay_. Sign "order|payment" with the secret "mock_secret"
ORDER=order_mock_ab12cd34ef56; PAY=pay_mock_ab12cd34ef56
SIG=$(echo -n "$ORDER|$PAY" | openssl dgst -sha256 -hmac mock_secret | awk '{print $NF}')

# 3. verify
curl -s -X POST localhost:8081/api/v1/payments/verify -H 'Content-Type: application/json' -d "{
  \"razorpay_order_id\": \"$ORDER\", \"razorpay_payment_id\": \"$PAY\", \"razorpay_signature\": \"$SIG\" }"
# -> status SUCCESS. Send it again: same result, no second event.  Change one character of SIG: 400.
```

### 4.3 With Razorpay Test Mode (the real thing)
1. Set the three env variables (2.2) and start all three services (event 8080, payment 8081, booking 8083).
2. Open **http://localhost:8083/razorpay-checkout-test.html** (served by the Booking service). It creates a booking, opens the Razorpay popup, and verifies the result - exactly the sequence your real frontend must implement.
3. Pay with Razorpay's test credentials:
   - UPI: `success@razorpay` (succeeds) / `failure@razorpay` (fails - then retry with the success one in the same popup)
   - Card: `4111 1111 1111 1111`, any future expiry, any CVV, OTP `1234` on the bank page
4. Watch the booking go `PENDING -> CONFIRMED`, and `GET /api/v1/payments/booking/{id}` show `SUCCESS` with `paymentMethod` filled from Razorpay.

Try the unhappy paths too: close the popup (booking expires after 10 min and the payment becomes `CANCELLED`), cancel a confirmed booking (refund appears in the Razorpay Dashboard), post the same verify call twice.

---

## 5. Webhooks

Razorpay calls `POST <public-url>/api/v1/payments/webhook` when a payment changes. This is how we learn about a payment when the customer closed the tab right after paying.

1. Razorpay cannot reach `localhost`. Expose it: `ngrok http 8081` -> `https://abcd.ngrok-free.app`.
2. Dashboard > **Settings > Webhooks > Add New Webhook**:
   - URL: `https://abcd.ngrok-free.app/api/v1/payments/webhook`
   - Secret: the value of `RAZORPAY_WEBHOOK_SECRET`
   - Events: `payment.captured`, `payment.failed`, `order.paid`, `refund.processed`, `refund.failed`
3. Restart is not needed. Pay once and look for `Webhook payment.captured (id evt_...) -> processed` in the log.

How the endpoint protects itself: the signature (HMAC-SHA256 of the **raw** body with the webhook secret) is checked before anything is parsed; every delivery's `X-Razorpay-Event-Id` is stored, so a re-delivery answers `duplicate` and changes nothing; events for orders that are not ours are acknowledged and ignored (Razorpay would otherwise retry for 24 hours).

**No webhook configured? Everything still works**, just slower: the browser verify path confirms immediately, and `PaymentSyncJob` (every `tickethub.payment.sync.delay-ms`, default 60 s) asks Razorpay about payments that have been quiet for 2 minutes. You can also force it: `POST /api/v1/payments/{id}/sync`.

---

## 6. Kafka contract for your Notification teammate (unchanged)

Topic `payment-events` - key `bookingId` - value:
```json
{ "eventType": "PAYMENT_SUCCESS", "paymentId": 1, "bookingId": 101, "userId": 7,
  "customerEmail": "asha@example.com", "amount": 500.00, "currency": "INR",
  "status": "SUCCESS", "failureReason": null, "occurredAt": "2026-09-30T10:15:30.123Z" }
```
`eventType` is `PAYMENT_SUCCESS`, `PAYMENT_FAILED` or `PAYMENT_REFUNDED`.

Two behaviours to tell them about (the format did not change, the *timing* is Razorpay-specific):
- `PAYMENT_FAILED` means **one attempt failed**; the customer may still pay on the same order. Another `PAYMENT_FAILED` or a `PAYMENT_SUCCESS` can follow. Do not treat it as "booking lost".
- A payment that lands after its booking was cancelled is refunded automatically and produces **only** `PAYMENT_REFUNDED` (no `PAYMENT_SUCCESS`).

---

## 7. Contract changes to confirm with teammates

You changed `POST /api/v1/payments`, so tell the Booking owner (the matching Booking changes are already included in `booking-service`):
1. **Request:** `paymentMethod` and `paymentToken` are gone; `idempotencyKey` is now meaningful.
2. **Response:** `providerTransactionId` is replaced by `providerOrderId` + `providerPaymentId`; new `razorpayKeyId`, `amountInMinorUnits`. Status can now be `PENDING` (it used to be final).
3. **Booking is no longer confirmed by the create call.** It is confirmed by verify, or by reconciliation after a webhook.
4. New endpoints Booking uses: `/verify`, `/booking/{id}/cancel`, `/booking/{id}/refund`.

---

## 8. Before you go live (checklist)

- [ ] Switch to **Live** keys (`rzp_live_...`); the app logs `LIVE (real money!)` at startup so you can confirm.
- [ ] Register the webhook with your **real https URL**, and use a strong random webhook secret.
- [ ] Expose **only** `/api/v1/payments/webhook` publicly. Put the rest behind the gateway / private network.
- [ ] Delete `razorpay-checkout-test.html` from booking-service.
- [ ] Confirm the dashboard capture setting is **Automatic** (the default) or set `RAZORPAY_MANUAL_CAPTURE=true`.
- [ ] Replace the placeholder customer email in Booking with the real one (Razorpay checkout prefill + receipts).
- [ ] Turn Kafka on (`tickethub.kafka.enabled=true`) and agree the contract in section 6.
- [ ] Set `ddl-auto` to `validate` and manage the schema with migrations (Flyway), as the event service does.
- [ ] Alert on the log lines `DOUBLE PAYMENT`, `AMOUNT MISMATCH`, `AUTO-REFUND FAILED`, `REFUND FAILED` - they mean a human must act.

---

## 9. Known limits (be honest about these in review)

- **Verified here:** the logic is covered by 52 unit tests that I ran, including signature checks against an independent HMAC test vector. **Not verified here:** real Razorpay calls, MySQL, Spring wiring and the 9 MockMvc web tests, because my environment could not download Maven dependencies. Run `mvn test` first, then the test-mode walkthrough in 4.3, and send me anything that fails.
- **Razorpay SDK 1.4.8** is pinned in `pom.xml`. If a newer one exists, bumping it is optional.
- **Currency:** only INR by default (`tickethub.payment.allowed-currencies`). The paise conversion assumes 2-decimal currencies.
- **Double payment on one order** (customer pays twice through two tabs) is detected and logged as `DOUBLE PAYMENT`, but the extra payment must be refunded manually.
- **Partial refunds** are not implemented; refunds are always for the full amount.
- **Two open orders for one booking** are prevented by an application check, not a database constraint; Booking uses a stable idempotency key, which closes the realistic race.
- **Kafka vs DB atomicity:** if Kafka is down at publish time the event is lost (the Transactional Outbox is the production fix; the blueprint defers it).
- No authentication on the internal endpoints yet (the blueprint adds security later).

---

## 10. Common problems

| Problem | Reason / fix |
|---|---|
| App fails at startup: `RAZORPAY_KEY_ID ... is not set` | Set the env variables (2.2) or run with `PAYMENT_GATEWAY=mock`. |
| `502 Razorpay could not create the order: Authentication failed` | Key id/secret mismatch, or test key used against live mode. Regenerate and copy both again. |
| `400 Invalid signature` on verify | Wrong secret, or you changed a value. The signature covers exactly `order_id|payment_id`. |
| Webhook returns `400 Invalid signature` | `RAZORPAY_WEBHOOK_SECRET` differs from the Dashboard webhook secret (it is NOT the API key secret). |
| Webhook never arrives locally | Razorpay cannot reach localhost: use ngrok, or rely on verify + the sync job. |
| Payment stays `PENDING` after paying | Browser verify never called and no webhook yet: `POST /api/v1/payments/{id}/sync`. |
| `409 Amount or currency ... does not match` | Razorpay's record differs from ours. Check the log line `AMOUNT MISMATCH`; do not force it. |
| Kafka warnings `Connection to node -1` | Kafka is off. Payments still work (`tickethub.kafka.enabled=false`). |
