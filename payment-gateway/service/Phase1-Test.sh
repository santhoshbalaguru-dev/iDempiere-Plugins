#!/usr/bin/env bash

set -a; [ -f .env ] && . ./.env; set +a

URL=localhost:8081/payment-links
KEY="X-API-Key: $PAYMENT_SERVICE_API_KEY"
JSON="Content-Type: application/json"
EXP=$(date -d '+10 days' +%Y-%m-%dT%H:%M:%S%:z)   # always in the future

body() {   # $1 = amount, $2 = optional gateway
  local gw=""
  [ -n "$2" ] && gw=",\"gateway\":\"$2\""
  cat <<EOF
{"adClientId":11,"adOrgId":11,"invoiceId":1004,"invoiceDocumentNo":"INV-1004",
 "amount":$1,"currency":"INR","customerName":"Test Customer",
 "customerEmail":"test@example.com","customerPhone":"+919876543210",
 "expireBy":"$EXP","description":"Invoice INV-1004"$gw}
EOF
}

call() { curl -s -w "\nHTTP %{http_code}\n\n" "$@"; }

echo "1) Create link (1500.50)";        call -X POST $URL -H "$KEY" -H "$JSON" -d "$(body 1500.50)"
echo "2) Same request (expect reused)"; call -X POST $URL -H "$KEY" -H "$JSON" -d "$(body 1500.50)"
echo "3) Changed amount (new link)";    call -X POST $URL -H "$KEY" -H "$JSON" -d "$(body 1600.00)"
echo "4) No API key (expect 401)";      call -X POST $URL -H "$JSON" -d "$(body 1600.00)"
echo "5) Bad amount (expect 400)";      call -X POST $URL -H "$KEY" -H "$JSON" -d "$(body 10.555)"
echo "6) Unknown gateway (expect 400)"; call -X POST $URL -H "$KEY" -H "$JSON" -d "$(body 1600.00 stripe)"