#!/usr/bin/env bash

set -a; [ -f .env ] && . ./.env; set +a

URL=localhost:8081/webhooks/razorpay
BODY='{"event":"payment_link.paid","payload":{"payment_link":{"entity":{"id":"plink_doesnotexist"}},"payment":{"entity":{"id":"pay_x","amount":100,"currency":"INR"}}}}'
SIG=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "$RAZORPAY_WEBHOOK_SECRET" | awk '{print $NF}')

call() { curl -s -w "\nHTTP %{http_code}\n\n" "$@"; }

echo "0) Clean test event"
PGPASSWORD="$DB_PASSWORD" psql -h localhost -U payment_service -d payment_service -c \
  "DELETE FROM processed_webhook_event WHERE event_id = 'evt_unknown_1';"

echo "1) Paid event for an unknown link (expect 503)"
call -X POST $URL -H "Content-Type: application/json" -H "x-razorpay-signature: $SIG" \
     -H "x-razorpay-event-id: evt_unknown_1" -d "$BODY"

echo "2) Same event again (expect 503 again, NOT 'duplicate')"
call -X POST $URL -H "Content-Type: application/json" -H "x-razorpay-signature: $SIG" \
     -H "x-razorpay-event-id: evt_unknown_1" -d "$BODY"

echo "3) Event ids stored (expect 0 rows)"
PGPASSWORD="$DB_PASSWORD" psql -h localhost -U payment_service -d payment_service -c \
  "SELECT event_id FROM processed_webhook_event WHERE event_id = 'evt_unknown_1';"