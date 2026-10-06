# Wiring notification-service into the other services

Status of the four problems that used to stop emails from reaching customers (found when this service was first written):

| # | Problem | Status |
|---|---|---|
| 1 | Kafka was switched off in booking-service and payment-service | **booking: fixed** (`booking.kafka.enabled` defaults to true). **payment: one line for you**, see below |
| 2 | booking-service had no JSON serializer for Kafka, so every event failed to send | **fixed** in booking-service `application.yml` |
| 3 | booking-service made up the customer's email (`customer7@example.com`) | **fixed**: the real address comes from the customer's login token and is stored on the booking |
| 4 | Port clash (user-service and payment-service both on 8081) | **fixed**: user 8082 |

## What you must still do: payment-service (your file has your keys, so I did not touch it)
```yaml
tickethub:
  kafka:
    enabled: true        # was false
```
Without it payment-service publishes nothing. The confirmation email does not depend on it (it comes from booking-service),
but failed-payment and refund emails do.

## Ports
| Service | Port |
|---|---|
| event-service (ticket-hub) | 8080 |
| payment-service | 8081 |
| user-service | 8082 |
| booking-service | 8083 |
| notification-service | 8085 |
| api-gateway | 8090 |
| service-registry | 8761 |

## How a confirmation email happens
```
customer pays -> booking-service marks the booking CONFIRMED and COMMITS to its database
              -> booking-service sends to Kafka topic "booking-events":
                   {bookingId, customerId, showId, status:"CONFIRMED", totalAmount,
                    customerEmail, showTitle, venue, showTime, seats:["A5","A6"]}
              -> notification-service (this service) reads it, builds the email, sends it by SMTP
              -> record in table "notifications": PENDING -> SENT (or FAILED -> retried automatically)
```
The message is sent only **after** booking-service's database commit, so a change that was rolled back never produces an email.
The dedupe key `BOOKING:CONFIRMED:<bookingId>` guarantees one email per booking even if Kafka delivers the message twice.

## Reaching this service through the API gateway
The gateway route for `/api/notifications/**` points at `lb://notification-service`, but this service does not register with
Eureka. Either set `NOTIFICATION_SERVICE_URI=http://localhost:8085` for the gateway, or add the Eureka client to this service.
Emails do **not** go through the gateway at all (they travel over Kafka); only the admin REST API does.

## Manual Kafka test (without the other services)
```bash
docker exec -i tickethub-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic booking-events
{"bookingId":1,"customerId":7,"showId":3,"status":"CONFIRMED","totalAmount":"700.00","customerEmail":"you@example.org","showTitle":"Inception","venue":"PVR Phoenix","showTime":"Sun 18 Oct 2026, 19:30","seats":["A5","A6"]}
```
(one JSON object per line, Ctrl-C to quit). Or run `./scripts/send-sample-events.sh`. Watch `GET /api/notifications` and the MailHog inbox at http://localhost:8025.

## Alternative without Kafka
Set `NOTIFICATION_KAFKA_ENABLED=false` and call `POST http://localhost:8085/api/notifications/trigger` after confirming (body in the README).
