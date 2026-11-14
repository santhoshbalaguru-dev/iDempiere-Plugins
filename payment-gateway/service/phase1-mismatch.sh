#!/usr/bin/env bash

set -a; [ -f .env ] && . ./.env; set +a

URL=localhost:8081/payment-links
KEY="X-API-Key: $PAYMENT_SERVICE_API_KEY"
JSON="Content-Type: application/json"
INV=9001                                   # not an iDempiere invoice
EXP=$(date -d '+10 days' +%Y-%m-%dT%H:%M:%S%:z)

sql() { PGPASSWORD="$DB_PASSWORD" psql -h localhost -U payment_service -d payment_service -c "$1"; }

body() {   # $1 = amount, $2 = optional extra json, e.g. ,"forceNew":true
  cat <<EOF
{"adClientId":11,"adOrgId":11,"invoiceId":$INV,"invoiceDocumentNo":"TEST-$INV",
 "amount":$1,"currency":"INR","customerName":"Test Customer",
 "customerEmail":"test@example.com","expireBy":"$EXP","description":"mismatch test"$2}
EOF
}

call() { curl -s -w "\nHTTP %{http_code}\n\n" "$@"; }

echo "0) Clean test rows";  sql "DELETE FROM payment_link WHERE invoice_id = $INV;"
echo "1) Create link (expect 200)"
call -X POST $URL -H "$KEY" -H "$JSON" -d "$(body 1500.50)"

echo "2) Simulate a mismatch that iDempiere has NOT recorded"
sql "UPDATE payment_link SET status='AMOUNT_MISMATCH', erp_synced_status=NULL, error_message='Paid 100 INR, expected 150050 INR' WHERE invoice_id=$INV AND status='CREATED';"

echo "3) Normal create (expect 409: not recorded yet, run Sync)"
call -X POST $URL -H "$KEY" -H "$JSON" -d "$(body 1500.50)"

echo "4) Simulate iDempiere recording it"
sql "UPDATE payment_link SET erp_synced_status='AMOUNT_MISMATCH' WHERE invoice_id=$INV AND status='AMOUNT_MISMATCH';"

echo "5) Normal create (expect 409: review, then use Regenerate)"
call -X POST $URL -H "$KEY" -H "$JSON" -d "$(body 1500.50)"

echo "6) Regenerate (expect 200, new link)"
call -X POST $URL -H "$KEY" -H "$JSON" -d "$(body 1500.50 ',"forceNew":true')"

echo "7) Normal create again (expect 200, reused true)"
call -X POST $URL -H "$KEY" -H "$JSON" -d "$(body 1500.50)"

echo "Rows:"; sql "SELECT id, status, erp_synced_status FROM payment_link WHERE invoice_id=$INV ORDER BY id;"