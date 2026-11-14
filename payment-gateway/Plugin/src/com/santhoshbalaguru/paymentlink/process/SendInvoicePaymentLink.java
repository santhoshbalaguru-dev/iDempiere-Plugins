package com.santhoshbalaguru.paymentlink.process;

import java.io.File;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.adempiere.base.annotation.Process;
import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MBPartner;
import org.compiere.model.MClient;
import org.compiere.model.MCurrency;
import org.compiere.model.MInvoice;
import org.compiere.model.MMailText;
import org.compiere.model.MUser;
import org.compiere.model.Query;
import org.compiere.print.ReportEngine;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.DB;
import org.compiere.util.EMail;
import org.compiere.util.Trx;
import java.nio.file.Path;

import com.santhoshbalaguru.paymentlink.client.PaymentServiceClient;
import com.santhoshbalaguru.paymentlink.client.PaymentServiceClient.LinkRequest;
import com.santhoshbalaguru.paymentlink.client.PaymentServiceClient.LinkResponse;
import com.santhoshbalaguru.paymentlink.config.PaymentGatewayConfig;
import com.santhoshbalaguru.paymentlink.store.InvoicePaymentLinkStore;

import com.santhoshbalaguru.paymentlink.sync.PaymentLinkSync;

/**
 * Send Invoice Payment Link: for each eligible customer invoice, asks the payment service for a
 * link for the open amount, records it (PL_InvoicePaymentLink + C_Invoice.PaymentLinkURL) and emails
 * the invoice PDF with the mail template, in which @PaymentLinkURL@ resolves from the invoice.
 *
 * Parameters: C_Invoice_ID, DateInvoiced (range), C_BPartner_ID, C_DocType_ID, AD_Org_ID,
 * DueDate (range), Resend, Regenerate, R_MailText_ID.
 */
@Process
public class SendInvoicePaymentLink extends SvrProcess {

	private int p_C_Invoice_ID, p_C_BPartner_ID, p_C_DocType_ID, p_AD_Org_ID, p_R_MailText_ID;
	private Timestamp p_InvoiceFrom, p_InvoiceTo, p_DueFrom, p_DueTo;
	private boolean p_Resend, p_Regenerate;	

	private final Map<Integer, PaymentGatewayConfig> configByOrg = new HashMap<>();

	/** An invoice that is left alone on purpose, with the reason. */
	private static final class Skip extends RuntimeException {
		private static final long serialVersionUID = 1L;

		Skip(String reason) {
			super(reason);
		}
	}

	@Override
	protected void prepare() {
		for (ProcessInfoParameter para : getParameter()) {
			String name = para.getParameterName();
			if (para.getParameter() == null && para.getParameter_To() == null)
				continue;
			if ("C_Invoice_ID".equals(name))
				p_C_Invoice_ID = para.getParameterAsInt();
			else if ("DateInvoiced".equals(name)) {
				p_InvoiceFrom = (Timestamp) para.getParameter();
				p_InvoiceTo = (Timestamp) para.getParameter_To();
			} else if ("C_BPartner_ID".equals(name))
				p_C_BPartner_ID = para.getParameterAsInt();
			else if ("C_DocType_ID".equals(name))
				p_C_DocType_ID = para.getParameterAsInt();
			else if ("AD_Org_ID".equals(name))
				p_AD_Org_ID = para.getParameterAsInt();
			else if ("DueDate".equals(name)) {
				p_DueFrom = (Timestamp) para.getParameter();
				p_DueTo = (Timestamp) para.getParameter_To();
			} else if ("Resend".equals(name))
				p_Resend = "Y".equals(para.getParameter());
			else if ("Regenerate".equals(name))
				p_Regenerate = "Y".equals(para.getParameter());
			else if ("R_MailText_ID".equals(name))
				p_R_MailText_ID = para.getParameterAsInt();
		}
	}

	@Override
	protected String doIt() throws Exception {
		boolean hasFilter = p_C_Invoice_ID > 0 || p_InvoiceFrom != null || p_InvoiceTo != null || p_C_BPartner_ID > 0
				|| p_C_DocType_ID > 0 || p_AD_Org_ID > 0 || p_DueFrom != null || p_DueTo != null;
		if (!hasFilter)
			throw new AdempiereException("Choose at least one filter (invoice, invoice date, business partner, "
					+ "document type, organization or due date) so the process cannot e-mail every open invoice.");
		
		// bring iDempiere up to date first: payments received since the last run are posted, so paid
		// invoices drop out of the selection and a recorded mismatch no longer blocks Regenerate.
		// A failed sync must not stop the sending; the service still guards against double payment.
		try {
			PaymentLinkSync.Result sync = new PaymentLinkSync(getCtx(), getAD_Client_ID(), get_TrxName(),
					(invoiceId, msg) -> addLog(invoiceId, null, null, msg, MInvoice.Table_ID, invoiceId)).run();
			addLog("Sync before sending: " + sync.summary());
		} catch (Exception e) {
			log.log(Level.WARNING, "Sync before sending failed", e);
			addLog("Sync before sending failed, continuing: " + e.getMessage());
		}

		List<MInvoice> invoices = findInvoices();
		int sent = 0, skipped = 0, failed = 0;
		for (MInvoice invoice : invoices) {
			try {
				handle(invoice);
				sent++;
				addLog(invoice.get_ID(), null, null, invoice.getDocumentNo() + ": payment link sent",
						MInvoice.Table_ID, invoice.get_ID());
			} catch (Skip s) {
				skipped++;
				addLog(invoice.get_ID(), null, null, invoice.getDocumentNo() + ": skipped, " + s.getMessage(),
						MInvoice.Table_ID, invoice.get_ID());
			} catch (Exception e) {
				failed++;
				log.log(Level.WARNING, "Payment link failed for invoice " + invoice.getDocumentNo(), e);
				addLog(invoice.get_ID(), null, null, invoice.getDocumentNo() + ": FAILED, " + e.getMessage(),
						MInvoice.Table_ID, invoice.get_ID());
			}
		}
		return "Invoices found: " + invoices.size() + ". Sent: " + sent + ", skipped: " + skipped + ", failed: "
				+ failed;
	}

	/** Completed, unpaid customer invoices (not credit memos) that match the parameters. */
	private List<MInvoice> findInvoices() {
		StringBuilder where = new StringBuilder("C_Invoice.IsSOTrx='Y' AND C_Invoice.Processed='Y'"
				+ " AND C_Invoice.DocStatus IN ('CO','CL') AND C_Invoice.IsPaid='N'"
				+ " AND EXISTS (SELECT 1 FROM C_DocType dt WHERE dt.C_DocType_ID=C_Invoice.C_DocType_ID"
				+ " AND dt.DocBaseType='ARI')");
		List<Object> params = new ArrayList<>();

		if (p_C_Invoice_ID > 0) { // a single invoice overrides the other filters
			where.append(" AND C_Invoice.C_Invoice_ID=?");
			params.add(p_C_Invoice_ID);
		} else {
			if (p_InvoiceFrom != null) {
				where.append(" AND C_Invoice.DateInvoiced>=?");
				params.add(p_InvoiceFrom);
			}
			if (p_InvoiceTo != null) {
				where.append(" AND C_Invoice.DateInvoiced<=?");
				params.add(p_InvoiceTo);
			}
			if (p_C_BPartner_ID > 0) {
				where.append(" AND C_Invoice.C_BPartner_ID=?");
				params.add(p_C_BPartner_ID);
			}
			if (p_C_DocType_ID > 0) {
				where.append(" AND C_Invoice.C_DocType_ID=?");
				params.add(p_C_DocType_ID);
			}
			if (p_AD_Org_ID > 0) {
				where.append(" AND C_Invoice.AD_Org_ID=?");
				params.add(p_AD_Org_ID);
			}
			if (p_DueFrom != null) {
				where.append(" AND paymenttermduedate(C_Invoice.C_PaymentTerm_ID, C_Invoice.DateInvoiced)>=?");
				params.add(p_DueFrom);
			}
			if (p_DueTo != null) {
				where.append(" AND paymenttermduedate(C_Invoice.C_PaymentTerm_ID, C_Invoice.DateInvoiced)<=?");
				params.add(p_DueTo);
			}
		}
		return new Query(getCtx(), MInvoice.Table_Name, where.toString(), get_TrxName()).setClient_ID()
				.setOnlyActiveRecords(true).setParameters(params)
				.setOrderBy("C_Invoice.DateInvoiced, C_Invoice.DocumentNo").list();
	}

	private void handle(MInvoice invoice) throws Exception {
		BigDecimal open = DB.getSQLValueBD(get_TrxName(), "SELECT invoiceOpen(?,0)", invoice.get_ID());
		if (open == null || open.signum() <= 0)
			throw new Skip("nothing open");
		if (MCurrency.getStdPrecision(getCtx(), invoice.getC_Currency_ID()) != 2)
			throw new Skip("only currencies with 2 decimals are supported");
		if (!p_Resend && !p_Regenerate
				&& InvoicePaymentLinkStore.hasActiveLink(getCtx(), invoice.get_ID(), get_TrxName()))
			throw new Skip("an active link already exists (set Resend to send it again, Regenerate for a new link)");

		MUser contact = findContact(invoice);
		if (contact == null)
			throw new Skip("no contact with an e-mail address");

		PaymentGatewayConfig cfg = config(invoice.getAD_Org_ID());
		int mailTextId = p_R_MailText_ID > 0 ? p_R_MailText_ID : cfg.mailTextId;
		if (mailTextId <= 0)
			throw new AdempiereException("No mail template: set one on the Payment Gateway Configuration or in the process parameter");

		MBPartner bp = MBPartner.get(getCtx(), invoice.getC_BPartner_ID());
		String docNo = invoice.getDocumentNo();
		LinkRequest request = new LinkRequest(invoice.getAD_Client_ID(), invoice.getAD_Org_ID(), invoice.get_ID(),
				truncate(docNo, 60), open.setScale(2, RoundingMode.HALF_UP),
				MCurrency.getISO_Code(getCtx(), invoice.getC_Currency_ID()), bp.getName(), contact.getEMail().trim(),
				OffsetDateTime.now().plusDays(cfg.expiryDays), truncate("Invoice " + docNo, 255), cfg.gateway,
				p_Regenerate);

		// link + database rows in their own transaction, so one failing invoice does not affect the others
		final PaymentServiceClient client = new PaymentServiceClient(cfg);
		final LinkResponse[] link = new LinkResponse[1];
		Trx.run(trxName -> {
			link[0] = client.createLink(request);
			InvoicePaymentLinkStore.save(getCtx(), invoice.getAD_Org_ID(), invoice.get_ID(), invoice.getC_Currency_ID(),
					link[0], trxName);
		});

		// the link is committed; a mail failure leaves it on the invoice, and Resend sends it again
		sendMail(invoice, contact.getEMail().trim(), mailTextId);
	}

	private void sendMail(MInvoice stale, String to, int mailTextId) throws Exception {
		MInvoice invoice = new MInvoice(getCtx(), stale.get_ID(), null); // re-read: includes PaymentLinkURL
		MMailText mailText = new MMailText(getCtx(), mailTextId, null);
		mailText.setPO(invoice);
		mailText.setBPartner(invoice.getC_BPartner_ID());
		String subject = mailText.getMailHeader();
		String message = mailText.getMailText(true);

		EMail email = MClient.get(getCtx()).createEMail(to, subject, message, mailText.isHtml());
		if (email == null)
			throw new AdempiereException("Could not create the e-mail; check the mail server settings on the Client");

		File pdf = null;
		File named = null;
		Path dir = null;
		try {
			pdf = ReportEngine.get(getCtx(), ReportEngine.INVOICE, invoice.get_ID(), null).getPDF();
			if (pdf == null)
				throw new AdempiereException("Could not create the invoice PDF");
			// own folder per mail, so two runs for the same invoice number never share a file
			dir = Files.createTempDirectory("invoice-mail-");
			named = dir.resolve("Invoice_" + invoice.getDocumentNo().replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf")
					.toFile();
			Files.copy(pdf.toPath(), named.toPath(), StandardCopyOption.REPLACE_EXISTING);
			email.addAttachment(named);

			String result = email.send();
			if (!EMail.SENT_OK.equals(result))
				throw new AdempiereException("E-mail not sent: " + result);
		} finally {
			deleteQuietly(named);
			deleteQuietly(pdf);
			deleteQuietly(dir == null ? null : dir.toFile());
		}
	}

	/** Temp files are not worth failing a mail over; a leftover is cleaned up by the OS later. */
	private static void deleteQuietly(File f) {
		if (f == null)
			return;
		try {
			Files.deleteIfExists(f.toPath());
		} catch (Exception e) {
			// ignore
		}
	}

	/** The invoice's contact if it has an e-mail address, else the first active contact of the partner that has one. */
	private MUser findContact(MInvoice invoice) {
		if (invoice.getAD_User_ID() > 0) {
			MUser user = MUser.get(getCtx(), invoice.getAD_User_ID());
			if (user != null && !blank(user.getEMail()))
				return user;
		}
		for (MUser user : MUser.getOfBPartner(getCtx(), invoice.getC_BPartner_ID(), get_TrxName())) {
			if (user.isActive() && !blank(user.getEMail()))
				return user;
		}
		return null;
	}

	private PaymentGatewayConfig config(int orgId) {
		PaymentGatewayConfig cfg = configByOrg.get(orgId);
		if (cfg == null) {
			cfg = PaymentGatewayConfig.load(getCtx(), orgId, get_TrxName());
			configByOrg.put(orgId, cfg);
		}
		return cfg;
	}

	private static boolean blank(String s) {
		return s == null || s.isBlank();
	}

	private static String truncate(String s, int max) {
		return s == null || s.length() <= max ? s : s.substring(0, max);
	}
}
