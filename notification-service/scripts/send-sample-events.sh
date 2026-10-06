#!/usr/bin/env bash
# Publishes one payment event and one booking event to Kafka (needs the tickethub-kafka container from docker-compose).
# Afterwards open MailHog at http://localhost:8025. You should see the booking confirmation (and, only if you turned
# notification.events.enabled.payment-success back on, a payment receipt too).
set -euo pipefail
KAFKA="docker exec -i tickethub-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092"
ID=${1:-$RANDOM}

echo "{\"eventType\":\"PAYMENT_SUCCESS\",\"paymentId\":$ID,\"bookingId\":$ID,\"userId\":7,\"customerEmail\":\"asha@example.org\",\"amount\":700.00,\"currency\":\"INR\",\"status\":\"SUCCESS\",\"failureReason\":null,\"occurredAt\":\"2026-10-03T12:00:00Z\"}" \
  | $KAFKA --topic payment-events

echo "{\"bookingId\":$ID,\"customerId\":7,\"showId\":3,\"status\":\"CONFIRMED\",\"totalAmount\":\"700.00\",\"customerEmail\":\"asha@example.org\",\"showTitle\":\"Inception\",\"venue\":\"PVR Phoenix\",\"showTime\":\"Sun 18 Oct 2026, 19:30\",\"seats\":[\"A5\",\"A6\"]}" \
  | $KAFKA --topic booking-events

echo "Sent sample events for booking $ID"
