-- Step 1: physical tables for the payment link plugin (PL_ prefix). Safe to re-run.
BEGIN;

CREATE TABLE IF NOT EXISTS PL_Config (
    PL_Config_ID      NUMERIC(10,0) NOT NULL,
    AD_Client_ID      NUMERIC(10,0) NOT NULL,
    AD_Org_ID         NUMERIC(10,0) NOT NULL,
    IsActive          CHAR(1)       NOT NULL DEFAULT 'Y',
    Created           TIMESTAMP     NOT NULL DEFAULT NOW(),
    CreatedBy         NUMERIC(10,0) NOT NULL,
    Updated           TIMESTAMP     NOT NULL DEFAULT NOW(),
    UpdatedBy         NUMERIC(10,0) NOT NULL,
    PL_Config_UU      VARCHAR(36)   DEFAULT NULL,
    Gateway           VARCHAR(30)   NOT NULL DEFAULT 'razorpay',
    ServiceUrl        VARCHAR(200)  NOT NULL,
    ServiceApiKey     VARCHAR(255)  NOT NULL,
    C_BankAccount_ID  NUMERIC(10,0) NOT NULL,
    R_MailText_ID     NUMERIC(10,0),
    ExpiryDays        NUMERIC(10,0) NOT NULL DEFAULT 15,
    CONSTRAINT PL_Config_Key PRIMARY KEY (PL_Config_ID),
    CONSTRAINT PL_Config_UU_idx UNIQUE (PL_Config_UU),
    CONSTRAINT PL_Config_IsActive_chk CHECK (IsActive IN ('Y','N')),
    CONSTRAINT CBankAccount_PLConfig FOREIGN KEY (C_BankAccount_ID)
        REFERENCES C_BankAccount(C_BankAccount_ID) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT RMailText_PLConfig FOREIGN KEY (R_MailText_ID)
        REFERENCES R_MailText(R_MailText_ID) DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE IF NOT EXISTS PL_InvoicePaymentLink (
    PL_InvoicePaymentLink_ID NUMERIC(10,0) NOT NULL,
    AD_Client_ID      NUMERIC(10,0) NOT NULL,
    AD_Org_ID         NUMERIC(10,0) NOT NULL,
    IsActive          CHAR(1)       NOT NULL DEFAULT 'Y',
    Created           TIMESTAMP     NOT NULL DEFAULT NOW(),
    CreatedBy         NUMERIC(10,0) NOT NULL,
    Updated           TIMESTAMP     NOT NULL DEFAULT NOW(),
    UpdatedBy         NUMERIC(10,0) NOT NULL,
    PL_InvoicePaymentLink_UU VARCHAR(36) DEFAULT NULL,
    C_Invoice_ID      NUMERIC(10,0) NOT NULL,
    Gateway           VARCHAR(30)   NOT NULL,
    GatewayLinkRef    VARCHAR(60),
    LinkURL           VARCHAR(255),
    Amount            NUMERIC       NOT NULL,
    C_Currency_ID     NUMERIC(10,0) NOT NULL,
    ExpireBy          TIMESTAMP,
    LinkStatus        VARCHAR(20)   NOT NULL,
    GatewayPaymentRef VARCHAR(60),
    PaidDate          TIMESTAMP,
    C_Payment_ID      NUMERIC(10,0),
    ServiceLinkNo     NUMERIC(10,0) NOT NULL,
    ErrorMessage      VARCHAR(2000),
    CONSTRAINT PL_InvoicePaymentLink_Key PRIMARY KEY (PL_InvoicePaymentLink_ID),
    CONSTRAINT PL_InvoicePaymentLink_UU_idx UNIQUE (PL_InvoicePaymentLink_UU),
    CONSTRAINT PL_InvoicePaymentLink_IsActive_chk CHECK (IsActive IN ('Y','N')),
    CONSTRAINT CInvoice_PLInvoicePaymentLink FOREIGN KEY (C_Invoice_ID)
        REFERENCES C_Invoice(C_Invoice_ID) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT CCurrency_PLInvoicePaymentLink FOREIGN KEY (C_Currency_ID)
        REFERENCES C_Currency(C_Currency_ID) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT CPayment_PLInvoicePaymentLink FOREIGN KEY (C_Payment_ID)
        REFERENCES C_Payment(C_Payment_ID) DEFERRABLE INITIALLY DEFERRED
);

-- each service link is recorded in iDempiere only once
CREATE UNIQUE INDEX IF NOT EXISTS PL_InvoicePaymentLink_Service
    ON PL_InvoicePaymentLink (Gateway, ServiceLinkNo);

-- current link, used by the mail template variable @PaymentLinkURL@
ALTER TABLE C_Invoice ADD COLUMN IF NOT EXISTS PaymentLinkURL VARCHAR(255);

COMMIT;
