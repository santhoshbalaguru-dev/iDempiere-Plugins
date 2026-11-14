package com.santhoshbalaguru.paymentlink.sync;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MCurrency;
import org.compiere.model.MInvoice;
import org.compiere.model.MPayment;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.process.DocAction;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Trx;

import com.santhoshbalaguru.paymentlink.client.PaymentServiceClient;
import com.santhoshbalaguru.paymentlink.client.PaymentServiceClient.SyncItem;
import com.santhoshbalaguru.paymentlink.config.PaymentGatewayConfig;
import com.santhoshbalaguru.paymentlink.store.InvoicePaymentLinkStore;
import java.math.BigDecimal;
import org.compiere.model.MBankAccount;

/**
 * Pulls links whose status changed in the payment service (paid, expired, cancelled, amount
 * mismatch), updates PL_InvoicePaymentLink, creates and completes the C_Payment for paid links and
 * confirms each link to the service. Used by Sync Payment Links and, before sending, by Send Invoice
 * Payment Link. Safe to run repeatedly: a link that already has a payment is never paid twice.
 */
public final class PaymentLinkSync {

	private static final CLogger log = CLogger.getCLogger(PaymentLinkSync.class);

	private static final int BATCH = 100;
	private static final int MAX_PAGES = 50; // 5000 links per run, a safety stop

	/** Receives one line per link, so the calling process can put it in its own log. */
	public interface Listener {
		void log(int invoiceId, String message);
	}

	public static final class Result {
		public int fetched, updated, payments, review, failed;

		public String summary() {
			return "Links from service: " + fetched + ". Updated: " + updated + ", payments created: " + payments
					+ ", needs review: " + review + ", failed: " + failed;
		}
	}

	/** What happened to one link. */
	private static final class Outcome {
		Integer paymentId;
		boolean created, review, unknown;
	}

	private final Properties ctx;
	private final int clientId;
	private final String trxName;
	private final Listener listener;

	public PaymentLinkSync(Properties ctx, int clientId, String trxName, Listener listener) {
		this.ctx = ctx;
		this.clientId = clientId;
		this.trxName = trxName;
		this.listener = listener;
	}

	public Result run() {
		Map<String, PaymentGatewayConfig> services = new LinkedHashMap<>();
		for (PaymentGatewayConfig c : PaymentGatewayConfig.loadAll(ctx, trxName))
			services.putIfAbsent(c.serviceUrl + "|" + c.apiKey, c);
		if (services.isEmpty())
			throw new AdempiereException(
					"No Payment Gateway Configuration found. Create one in the Payment Gateway Configuration window.");

		Result res = new Result();
		for (PaymentGatewayConfig cfg : services.values()) {
			PaymentServiceClient client = new PaymentServiceClient(cfg);
			long afterId = 0; // a link that fails is skipped for this run, so it cannot hold up the rest
			for (int page = 0; page < MAX_PAGES; page++) {
				List<SyncItem> items = client.fetchUnsynced(clientId, afterId, BATCH);
				if (items.isEmpty())
					break;
				for (SyncItem item : items) {
					afterId = Math.max(afterId, item.id);
					res.fetched++;
					try {
						Outcome out = apply(item);
						client.markSynced(item.id, item.status, out.paymentId);
						if (out.unknown) {
							listener.log(item.invoiceId,
									"Link " + item.id + " (" + item.status + "): not in iDempiere, ignored");
							continue;
						}
						res.updated++;
						if (out.created)
							res.payments++;
						if (out.review)
							res.review++;
						listener.log(item.invoiceId, "Link " + item.id + ": " + item.status
								+ (out.created ? ", payment created" : "") + (out.review ? ", NEEDS REVIEW" : ""));
					} catch (Exception e) {
						res.failed++;
						log.log(Level.WARNING, "Sync failed for service link " + item.id, e);
						listener.log(item.invoiceId, "Link " + item.id + ": FAILED, " + e.getMessage());
					}
				}
				if (items.size() < BATCH)
					break;
			}
		}
		return res;
	}

	/** Own transaction per link: the row update and the payment are committed together or not at all. */
	private Outcome apply(SyncItem item) {
		Outcome out = new Outcome();
		Trx.run(trx -> {
			PO row = new Query(ctx, InvoicePaymentLinkStore.TABLE, "Gateway=? AND ServiceLinkNo=?", trx)
					.setClient_ID().setParameters(item.gateway, Math.toIntExact(item.id)).first();
			if (row == null) {
				if ("PAID".equals(item.status) || "AMOUNT_MISMATCH".equals(item.status))
					throw new AdempiereException("Money was received for service link " + item.id
							+ " but it has no row in " + InvoicePaymentLinkStore.TABLE
							+ "; find the invoice and post it by hand");
				out.unknown = true; // for example a test link made with curl
				return;
			}

			int invoiceId = row.get_ValueAsInt("C_Invoice_ID");
			row.set_ValueOfColumn("LinkStatus", item.status);
			if (item.paidAt != null)
				row.set_ValueOfColumn("PaidDate", Timestamp.from(item.paidAt.toInstant()));
			if (item.gatewayPaymentId != null)
				row.set_ValueOfColumn("GatewayPaymentRef", item.gatewayPaymentId);
			row.set_ValueOfColumn("ErrorMessage", truncate(item.errorMessage, 2000));

			// the link is finished, so it must not stay on the invoice (mail template @PaymentLinkURL@)
			DB.executeUpdateEx("UPDATE C_Invoice SET PaymentLinkURL=NULL WHERE C_Invoice_ID=? AND PaymentLinkURL=?",
					new Object[] { invoiceId, row.get_ValueAsString("LinkURL") }, trx);

			if ("PAID".equals(item.status))
				postPayment(row, item, invoiceId, trx, out);
			else if ("AMOUNT_MISMATCH".equals(item.status))
				out.review = true; // the customer paid a different amount: a person decides
			row.saveEx();
		});
		return out;
	}

	private void postPayment(PO row, SyncItem item, int invoiceId, String trx, Outcome out) {
		int existing = row.get_ValueAsInt("C_Payment_ID");
		if (existing > 0) { // an earlier run posted it and only the confirmation to the service failed
			out.paymentId = existing;
			return;
		}

		MInvoice invoice = new MInvoice(ctx, invoiceId, trx);
		if (invoice.isPaid()) {
			row.set_ValueOfColumn("ErrorMessage",
					"Paid at the gateway, but the invoice is already paid. No payment created; review needed.");
			out.review = true;
			return;
		}
		String invoiceCurrency = MCurrency.getISO_Code(ctx, invoice.getC_Currency_ID());
		if (!invoiceCurrency.equalsIgnoreCase(item.currency))
			throw new AdempiereException("Paid in " + item.currency + " but the invoice is in " + invoiceCurrency);

		// another payment may have been recorded since the link was created
		BigDecimal open = DB.getSQLValueBD(trx, "SELECT invoiceOpen(?,0)", invoiceId);
		if (open == null || open.compareTo(item.amount) < 0) {
			row.set_ValueOfColumn("ErrorMessage", "Paid " + item.amount + " " + item.currency
					+ " at the gateway, but only " + open + " is open on the invoice. "
					+ "No payment created; review needed.");
			out.review = true;
			return;
		}

		PaymentGatewayConfig cfg = PaymentGatewayConfig.load(ctx, invoice.getAD_Org_ID(), trx);
		if (cfg.bankAccountId <= 0)
			throw new AdempiereException("No bank account on the Payment Gateway Configuration");
		if (MBankAccount.get(ctx, cfg.bankAccountId).getC_Currency_ID() != invoice.getC_Currency_ID())
			throw new AdempiereException("The bank account on the Payment Gateway Configuration is not in the invoice currency ("
					+ invoiceCurrency + ")");

		Timestamp paidAt = item.paidAt != null ? Timestamp.from(item.paidAt.toInstant())
				: new Timestamp(System.currentTimeMillis());
		MPayment pay = new MPayment(ctx, 0, trx);
		pay.setAD_Org_ID(invoice.getAD_Org_ID());
		pay.setC_DocType_ID(true); // customer receipt
		pay.setC_BankAccount_ID(cfg.bankAccountId);
		pay.setTenderType(MPayment.TENDERTYPE_DirectDeposit);
		pay.setC_BPartner_ID(invoice.getC_BPartner_ID());
		pay.setC_Invoice_ID(invoiceId);
		pay.setC_Currency_ID(invoice.getC_Currency_ID());
		pay.setPayAmt(item.amount);
		pay.setDateTrx(paidAt);
		pay.setDateAcct(paidAt);
		pay.setR_PnRef(item.gatewayPaymentId);
		pay.setDescription(item.gateway + " " + item.gatewayPaymentId + " / invoice " + invoice.getDocumentNo());
		pay.setDocAction(DocAction.ACTION_Complete);
		pay.saveEx();
		if (!pay.processIt(DocAction.ACTION_Complete))
			throw new AdempiereException("Could not complete the payment: " + pay.getProcessMsg());
		pay.saveEx();

		row.set_ValueOfColumn("C_Payment_ID", pay.get_ID());
		out.paymentId = pay.get_ID();
		out.created = true;
	}

	private static String truncate(String s, int max) {
		return s == null || s.length() <= max ? s : s.substring(0, max);
	}
}