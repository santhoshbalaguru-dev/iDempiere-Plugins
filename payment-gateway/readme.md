# Payment Gateway (iDempiere payment links)

Customers receive their invoice PDF by e-mail together with a payment link. When they pay, the payment
is posted automatically in iDempiere and the invoice is marked paid.

Two parts:

- `service/`: Spring Boot service (port 8081) that creates payment links at the gateway (Razorpay today),
  receives the gateway's webhooks and keeps the link status.
- `Plugin/`: iDempiere OSGi bundle `com.santhoshbalaguru.paymentlink` with two processes:
  **Send Invoice Payment Link** (creates the link and e-mails the invoice PDF with it) and
  **Sync Payment Links** (pulls changed links from the service and posts received payments).

```
iDempiere --POST /payment-links--> service --> Razorpay
Customer pays --> Razorpay --webhook--> service (link becomes PAID)
iDempiere --Sync Payment Links (scheduler, every N minutes)--> pulls PAID links
          --> creates and completes the C_Payment --> confirms to the service (/synced)
```

The service never calls iDempiere. iDempiere always calls the service (pull model), so the service can run
on the same machine and only the webhook endpoint needs to be reachable from the internet.

## Contents

1. [Requirements](#1-requirements)
2. [Set up the service](#2-set-up-the-service)
3. [Connect Razorpay](#3-connect-razorpay)
4. [Install the iDempiere plugin](#4-install-the-idempiere-plugin)
5. [Configure iDempiere](#5-configure-idempiere)
6. [Try it end to end](#6-try-it-end-to-end)
7. [Daily use](#7-daily-use)
8. [Link behaviour](#8-link-behaviour)
9. [Testing](#9-testing)
10. [Troubleshooting](#10-troubleshooting)
11. [Going to production](#11-going-to-production)
12. [Known limits](#12-known-limits)

## 1. Requirements

| Need | Notes |
|---|---|
| Java 17 | Service and plugin both target 17 |
| PostgreSQL | Two databases: the iDempiere database and a separate `payment_service` database |
| iDempiere 13 | Plugin requires `org.adempiere.base` and `org.adempiere.plugin.utils` 13.0.0 or later |
| Razorpay account | Test mode is enough to start; you need the Key ID, Key Secret and a webhook secret |
| Eclipse with PDE | The plugin has no pom; it is built in Eclipse against the iDempiere target platform |
| A public HTTPS URL | Only for the Razorpay webhook (a tunnel such as cloudflared is fine in development) |
| Mail server settings on the iDempiere Client | Used to send the invoice mail |

The SQL scripts assume that Invoice (Customer) is window ID 167 and that native sequences are off.

## 2. Set up the service

1. Create the database and user:

   ```bash
   sudo -u postgres psql -c "CREATE USER payment_service WITH PASSWORD 'change-me';"
   sudo -u postgres psql -c "CREATE DATABASE payment_service OWNER payment_service;"
   ```

2. In `service/`, copy the settings file and fill it in:

   ```bash
   cd service
   cp .env.example .env
   ```

   | Variable | Meaning |
   |---|---|
   | `DB_URL`, `DB_USER`, `DB_PASSWORD` | The `payment_service` database |
   | `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` | From the Razorpay dashboard (test or live keys) |
   | `RAZORPAY_WEBHOOK_SECRET` | The secret you set on the Razorpay webhook (step 3). While it is empty every webhook is rejected with 401 |
   | `PAYMENT_SERVICE_API_KEY` | Any long random string. It must match **Service API Key** in the iDempiere configuration window |

   `.env` is git-ignored. The service reads it from its working directory, so always start it from `service/`.

3. Start the service:

   ```bash
   ./mvnw spring-boot:run
   ```

   Flyway creates the schema (migrations V1 to V5) on first start. Check it is up:

   ```bash
   curl http://localhost:8081/actuator/health
   ```

   For a permanent installation build a jar instead (see [Going to production](#11-going-to-production)).

Two background jobs run inside the service: the expiry sweep (every 5 minutes) marks links that are past
their expiry time as `EXPIRED`, as a backup for a lost Razorpay expiry webhook. Interval settings:
`gateway.expiry-sweep-ms` (default 300000) and `gateway.expiry-sweep-initial-ms` (default 60000).

## 3. Connect Razorpay

1. In the Razorpay dashboard (test mode first) create the API keys and put them in `.env`.
2. Make the service reachable from the internet. In development, use a tunnel:

   ```bash
   cloudflared tunnel --url http://localhost:8081
   ```

   Note the public `https://...` address it prints. Quick tunnels change address on every start, so update the
   Razorpay webhook each time, or use a named tunnel.
3. In Razorpay: Settings > Webhooks > Add. Set:
   - URL: `https://<public-host>/webhooks/razorpay`
   - Secret: the same value as `RAZORPAY_WEBHOOK_SECRET`
   - Events: `payment_link.paid`, `payment_link.expired`, `payment_link.cancelled`
4. Restart the service if you changed `.env`.

Webhooks are authenticated by Razorpay's signature (HMAC-SHA256 over the raw body), not by the API key.
Duplicate deliveries are ignored, and a payment for a link the service does not know is answered with 503 so
that Razorpay retries it.

## 4. Install the iDempiere plugin

### 4.1 Database scripts

Run the scripts in `Plugin/db/` on the **iDempiere** database, in this order, with the iDempiere database
user (for example `adempiere`). Every script is safe to re-run. If one fails partway, run `ROLLBACK;` before
trying again.

```bash
cd Plugin/db
psql -h localhost -U adempiere -d <idempiere_db> -f pl_step1_tables.sql
# ...and so on for each script in the table below
```

| Order | Script | What it does |
|---|---|---|
| 1 | `pl_step1_tables.sql` | PL_Config, PL_InvoicePaymentLink, indexes, C_Invoice.PaymentLinkURL |
| 2 | `pl_step2_register.sql` | Entity type PL, status list, table records, ID sequences |
| 3 | `pl_step3_columns.sql` | Elements and column definitions (do **not** run "Create Columns from DB") |
| 4 | `pl_step4_windows.sql` | Configuration window, Payment Links tab on window 167, fields |
| 5 | `pl_step5_menu_access.sql` | Menu entry and role access for the window |
| 6 | `pl_step6_verify.sql` | Read-only checks (run any time) |
| 7 | `pl_step7_process.sql` | Send Invoice Payment Link process, menu, access |
| 8 | `pl_step8_regenerate.sql` | Regenerate parameter |
| 9 | `pl_step9_sync_process.sql` | Sync Payment Links process, menu, access |
| 10 | `pl_step10_scheduler.sql` | Scheduler every N minutes (**edit client, user and minutes at the top first**) |
| 11 | `pl_step11_mask_apikey.sql` | Shows the API key as a password field |
| 12 | `pl_step12_config_unique.sql` | One active configuration per client and organization |

Notes:

- Step 4 stops with an error if window 167 does not exist.
- Step 5 puts the menu entry in the same folder as an "E-Invoice ... Config" entry when there is one, otherwise
  in the menu root. Access goes to the roles that can open the E-Invoice configuration window, plus System
  Administrator.
- Step 7 gives the Send process to the roles that can open the Payment Gateway Configuration window. To let a
  normal billing role run it, add it in Role > Process Access (and Window Access for the Invoice window if it
  does not have it).
- Step 10: at the top of the script set `client_id`, `org_id`, `supervisor_name` (an iDempiere user with an
  active role; the scheduler runs as that user and that user is notified when a run fails) and `minutes`.
  Changing the minutes and re-running points the scheduler at the new schedule.
- Run `pl_step6_verify.sql` after the scripts. Expected: configuration tab 10 fields, Payment Links tab 17 fields,
  `PaymentLinkURL` field on the invoice header, one menu row per client menu tree.

### 4.2 Build and start the bundle

1. In Eclipse (with the iDempiere target platform loaded) import `Plugin/` as an existing project. The bundle
   symbolic name is `com.santhoshbalaguru.paymentlink`. It builds with PDE; there is no pom.
2. Start it with the server the same way you start the E-Invoice bundle: either add it to the iDempiere launch
   configuration (development), or export it (Export > Deployable plug-ins and fragments) and install the jar
   into the running server.
3. In the OSGi console check that the bundle is active and its process factory is registered:

   ```
   ss paymentlink
   services (objectClass=org.adempiere.base.IProcessFactory)
   ```

   The bundle must show `ACTIVE`. After a code change, rebuild and restart the bundle (or the server).

### 4.3 Reset caches

After the scripts: System Admin > General Rules > Sequence Check, then Cache Reset, and log in again.
**Restart iDempiere once after step 10**: the server reads schedulers at startup and does not notice schedulers
created with SQL while it runs.

## 5. Configure iDempiere

1. **Mail server.** On the Client record, make sure the mail settings (SMTP host, user, password, request e-mail)
   work. The Send process uses the Client's mail setup.
2. **Mail template** (Mail Template window): create or pick a template for the invoice mail. Put
   `@PaymentLinkURL@` in the text (for example "Pay online: @PaymentLinkURL@"). It resolves from the invoice.
   Other invoice columns such as `@DocumentNo@` work too.
3. **Bank account.** A bank account in the currency of the invoices (INR for Razorpay). Received payments are
   posted to it.
4. **Payment Gateway Configuration** window (menu):

   | Field | Value |
   |---|---|
   | Organization | `*` for client-wide, or one organization. An organization's own record wins over the `*` one |
   | Gateway | `razorpay` |
   | Service URL | `http://localhost:8081` (no trailing slash). Anything that is not this machine **must** use `https://`, because the API key is sent with every call |
   | Service API Key | The same value as `PAYMENT_SERVICE_API_KEY` in the service `.env` |
   | Bank Account | The account from step 3 |
   | Mail Template | The template from step 2 (can be overridden when running the process) |
   | Expiry Days | Days until a new link expires (default 15) |

   Only one active record per client and organization is allowed.
5. **Customers.** Each customer needs a contact with an e-mail address. The invoice's contact is used first, then
   the first active contact of the business partner that has an e-mail.

## 6. Try it end to end

1. Complete a customer invoice (document type with base type Accounts Receivable Invoice) in INR, with an
   open amount.
2. Open **Send Invoice Payment Link**, set Invoice to that invoice and run it. Expected log line:
   `<DocumentNo>: payment link sent`. The customer receives the invoice PDF and the link, and the invoice
   gets a row in its **Payment Links** tab (status `CREATED`) and the link in its Payment Link URL field.
3. Open the link and pay with one of Razorpay's test payment methods.
4. Razorpay calls the webhook, and the service marks the link `PAID` (check the service log).
5. Wait for the scheduler, or run **Sync Payment Links** by hand. The Payment Links row becomes `PAID`, a
   customer payment is created and completed, and the invoice is paid.

If something does not match, see [Troubleshooting](#10-troubleshooting).

## 7. Daily use

### Send Invoice Payment Link

Choose at least one filter, so the process can never e-mail every open invoice at once.

| Parameter | Meaning |
|---|---|
| Invoice | One invoice only; the other filters are then ignored |
| Invoice Date (range) | Invoices dated within the range |
| Business Partner | One customer |
| Document Type | One document type |
| Organization | One organization |
| Due Date (range) | Invoices falling due within the range |
| Resend Link | Also handle invoices that already have an active link: the same link is e-mailed again |
| Regenerate Link | Cancel the active link at the gateway and create a new one. The old row stays in the tab as Cancelled |
| Mail Template | Overrides the template from the configuration |

Eligible invoices are completed or closed, unpaid, customer invoices (not credit memos) with an open amount,
in a currency with 2 decimals. Others are skipped with the reason in the process log. A failure on one invoice
does not stop the others. If the mail fails after the link was created, the link stays on the invoice and Resend
sends it again.

Before sending, the process runs a sync, so payments received since the last run are posted first.

### Sync Payment Links

No parameters. It updates every changed link (paid, expired, cancelled, amount mismatch), creates and completes
the customer payment for each paid link, and confirms each link to the service. It is safe to run at any time and
as often as you like; a link that already has a payment is never paid twice. It also runs automatically on the
scheduler from step 10.

Check the scheduler after a server restart:

```sql
SELECT pi.Created, pi.IsProcessing, pi.Result, pi.ErrorMsg
  FROM AD_PInstance pi JOIN AD_Process p ON p.AD_Process_ID = pi.AD_Process_ID
 WHERE p.Value = 'PL_SyncPaymentLinks'
 ORDER BY pi.Created DESC LIMIT 10;
```

### Link statuses (Payment Links tab)

| Status | Meaning |
|---|---|
| CREATED | Active link, waiting for payment |
| PAID | Paid in full; payment posted in iDempiere |
| EXPIRED | Passed its expiry time unpaid |
| CANCELLED | Cancelled (replaced by a new link or cancelled at the gateway) |
| FAILED | The gateway call failed (see Error Message) |
| AMOUNT_MISMATCH | The customer paid a different amount; not posted, needs review |

A link can be PAID with a note in Error Message ("review needed"): that means money arrived but no payment was
created, for example because the invoice was already paid or has less open than was paid.

## 8. Link behaviour

- One active link per invoice. Same amount: the existing link is reused. Changed amount or expired link: it is
  replaced (the old one is cancelled at the gateway, or just marked expired).
- **Resend**: send the existing link again. **Regenerate**: cancel it and create a new one.
- A payment that iDempiere has not processed yet blocks new links for that invoice (run Sync Payment Links first).
- A payment of a different amount (AMOUNT_MISMATCH), and a paid link that iDempiere flagged for review without
  creating a payment, both need a person to review them in iDempiere. After that, only **Regenerate** issues a
  new link.
- Payments are only posted when the amount and currency match what was asked and the invoice still has at least
  that amount open. Partial payments are not accepted at the gateway.

## 9. Testing

Unit tests (service):

```bash
cd service
./mvnw test
```

`contextLoads` starts the whole application and only runs when `.env` or `DB_PASSWORD` is present.

Manual checks against a running service (run from `service/`; they read `.env`, use client 11 and organization
11, and need Razorpay test mode):

| Script | Checks |
|---|---|
| `Phase1-Test.sh` | Create, reuse, changed amount, missing API key (401), bad amount (400), unknown gateway (400) |
| `phase1-mismatch.sh` | Mismatch handling: blocked until iDempiere records it, then Regenerate (uses `psql` on the service database) |
| `webhook-unknown-link.sh` | A paid webhook for an unknown link returns 503 and is not stored as processed |

## 10. Troubleshooting

| Symptom | Likely cause and fix |
|---|---|
| Every call to the service returns 401 | The API key differs. `PAYMENT_SERVICE_API_KEY` in `.env`, the key in your shell (for the scripts) and **Service API Key** in iDempiere must all be identical |
| Webhook returns 401 | `RAZORPAY_WEBHOOK_SECRET` is empty or different from the secret on the Razorpay webhook |
| Webhook returns 503 | A paid event for a link the service does not have. Expected for test events; Razorpay retries |
| Service 409 "waiting to be posted" | Money was received and iDempiere has not processed it: run Sync Payment Links |
| Service 409 "use Regenerate" | A different-amount or review-needed payment exists: review it, then run Send with Regenerate |
| Service 502 `gateway_error` | Razorpay rejected the call (keys, amount, expiry). The message is in the response and in the link's Error Message |
| "Payment service not reachable" | Service not running, or wrong Service URL |
| "Service URL must use https" | Only localhost may use http |
| "No Payment Gateway Configuration found" | No active configuration for the invoice's organization or for `*` |
| "E-mail not sent" or "Could not create the e-mail" | Fix the mail settings on the Client |
| Process window does not open | Re-run `pl_step7_process.sql` (it sets ShowHelp) and reset the cache |
| Amount or link fields not editable/saving | Re-run `pl_step3_columns.sql`; it repairs column definitions |
| A scheduler change has no effect | Restart iDempiere; SQL-created schedulers are read only at startup |
| Paid at Razorpay but nothing in iDempiere | Check in order: service log (webhook arrived?), the link status in the `payment_link` table, then run Sync Payment Links and read its log |
| API key stored in clear text | The iDempiere server has no secure class configured (`ADEMPIERE_SECURE`); `pl_step6_verify.sql` shows `stored_encrypted` |

Logs: the service writes to its console; the processes write a line per invoice or link to the iDempiere
process log.

## 11. Going to production

1. Use Razorpay **live** keys, and create the webhook again in live mode with its own secret.
2. Give the service a stable public HTTPS address for `/webhooks/razorpay` (a named tunnel or a reverse proxy).
   Do not expose `/payment-links` to the internet; only iDempiere needs it.
3. Build and run the jar from the directory that holds `.env`:

   ```bash
   cd service
   ./mvnw -DskipTests package
   java -jar target/payment-gateway-service-0.0.1-SNAPSHOT.jar
   ```

   Run it under a service manager so it restarts on failure, for example a systemd unit with
   `WorkingDirectory` set to the `service/` folder and `ExecStart=/usr/bin/java -jar ...`.
4. Use a strong `PAYMENT_SERVICE_API_KEY`, and keep `.env` readable only by the service user.
5. Back up the `payment_service` database together with the iDempiere database. The service keeps the
   link and webhook history; iDempiere keeps the payments.
6. Make sure the scheduler (step 10) is set for the production client and a real supervisor user, and that
   `/actuator/health` is monitored.
7. If the service and iDempiere are on different machines, the Service URL must be `https://`.

## 12. Known limits

- Refunds are not handled. If a paid link is refunded at the gateway, the customer payment stays posted in
  iDempiere and must be reversed by hand.
- A link paid with a different amount is not posted automatically. It stays AMOUNT_MISMATCH until a person
  reviews it in iDempiere; after that, Regenerate issues a new link.
- Payments are posted with tender type Direct Deposit whatever the customer used (UPI, card, net banking),
  and at the gross amount; gateway fees are not recorded.
- Only currencies with 2 decimal places are supported.
- Partial payments are not accepted: a link is paid in full or not at all.
- One service API key is shared by all clients and organizations that use the same service.
