#!/usr/bin/env bash
# Usage: ./scripts/send-test-email.sh someone@gmail.com
set -euo pipefail
TO=${1:?usage: send-test-email.sh <email>}
curl -s -X POST http://localhost:8085/api/notifications/email \
  -H "Content-Type: application/json" \
  ${NOTIFICATION_API_KEY:+-H "X-API-Key: $NOTIFICATION_API_KEY"} \
  -d "{\"to\":\"$TO\",\"subject\":\"TicketHub test email\",\"message\":\"If you can read this, the notification service works.\"}"
echo
