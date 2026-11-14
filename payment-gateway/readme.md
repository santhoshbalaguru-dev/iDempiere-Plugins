# Payment Gateway (iDempiere payment links)

Two parts:

- `service/`: Spring Boot service (port 8081) that creates payment links at the gateway (Razorpay today),
  receives the gateway's webhooks and keeps the link status.
- `Plugin/`: iDempiere OSGi bundle `com.santhoshbalaguru.paymentlink` with two processes:
  **Send Invoice Payment Link** (creates the link and e-mails the invoice PDF with it) and
  **Sync Payment Links** (pulls changed links from the service and posts received payments).

Flow: iDempiere -> service `POST /payment-links` -> gateway. Customer pays -> gateway webhook ->
service marks the link PAID -> Sync Payment Links (scheduler, every 5 minutes) pulls it,
creates and completes the C_Payment, and confirms it to the service.

## Service

1. Create the PostgreSQL database and user (default `payment_service`).
2. `cp .env.example .env` and fill it in.
3. Run, from `service/`:

```bash
   source .env
   ./mvnw spring-boot:run
```

   Flyway creates the schema on start.
4. Razorpay: add a webhook pointing at `https://<public-host>/webhooks/razorpay` with the secret from
   `RAZORPAY_WEBHOOK_SECRET`, and enable `payment_link.paid`, `payment_link.expired`,
   `payment_link.cancelled`. During development expose port 8081 through a tunnel.
5. Tests: `./mvnw test`. `contextLoads` runs only when `.env` or `DB_PASSWORD` is present.
   `Phase1-Test.sh` and `Phase1-MismatchTest.sh` are manual curl checks against a running service.

## iDempiere plugin

Run the SQL in `Plugin/db/` on the iDempiere database, in this order. Every script is safe to re-run.

| Order | Script | What it does |
|---|---|---|
| 1 | `pl_step1_tables.sql` | PL_Config, PL_InvoicePaymentLink, C_Invoice.PaymentLinkURL |
| 2 | `pl_step2_register.sql` | Entity type PL, status list, table records, ID sequences |
| 3 | `pl_step3_columns.sql` | Elements and column definitions |
| 4 | `pl_step4_windows.sql` | Configuration window, Payment Links tab on window 167, fields |
| 5 | `pl_step5_menu_access.sql` | Menu entry and role access for the window |
| 6 | `pl_step6_verify.sql` | Read-only checks (run any time) |
| 7 | `pl_step7_process.sql` | Send Invoice Payment Link process, menu, access |
| 8 | `pl_step8_regenerate.sql` | Regenerate parameter |
| 9 | `pl_step9_sync_process.sql` | Sync Payment Links process, menu, access |
| 10 | `pl_step10_scheduler.sql` | Scheduler every N minutes (edit client, user and minutes at the top) |
| 11 | `pl_step11_mask_apikey.sql` | Shows the API key as a password field |

After the scripts: System Admin > Cache Reset, log in again, and restart iDempiere once after step 10.
The scripts assume Invoice (Customer) is window 167 and that native sequences are off.

Then open **Payment Gateway Configuration** and set Gateway, Service URL (`http://localhost:8081`),
Service API Key (same as `PAYMENT_SERVICE_API_KEY`), Bank Account and a Mail Template that contains
`@PaymentLinkURL@`. Non-local service URLs must use https.

Build the bundle in Eclipse (no pom) and start it with the server.

## Link behaviour

- One active link per invoice. Same amount: the link is reused. Changed amount or expired: replaced.
- **Resend**: send the existing link again. **Regenerate**: cancel it and create a new one.
- A paid link not yet posted blocks new links. A link paid with a different amount (AMOUNT_MISMATCH)
  needs review in iDempiere; after that only Regenerate issues a new link.
  ## Known limits
- Refunds are not handled. If a paid link is refunded at the gateway, the customer payment stays
  posted in iDempiere and must be reversed by hand.
- A link paid with a different amount is not posted automatically. It stays AMOUNT_MISMATCH until a
  person reviews it in iDempiere; after that, Regenerate issues a new link.
- Only currencies with 2 decimal places are supported.
- Partial payments are not accepted: a link is paid in full or not at all.
