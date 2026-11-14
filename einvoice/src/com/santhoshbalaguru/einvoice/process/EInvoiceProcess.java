package com.santhoshbalaguru.einvoice.process;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.adempiere.base.annotation.Process;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.model.GenericPO;
import org.compiere.model.MInvoice;
import org.compiere.model.Query;
import org.compiere.process.DocAction;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Trx;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.compiere.model.MSysConfig;
import org.compiere.util.CacheMgt;

import com.santhoshbalaguru.einvoice.config.EInvoiceConfig;
import com.santhoshbalaguru.einvoice.model.MSBInvoice;
import com.santhoshbalaguru.einvoice.service.EInvoiceAuthService;
import com.santhoshbalaguru.einvoice.service.EInvoiceGenerateIrnService;
import com.santhoshbalaguru.einvoice.service.EInvoiceGenerateIrnService.IrnResult;
import com.santhoshbalaguru.einvoice.service.EInvoiceTokenCache;

@Process
public class EInvoiceProcess extends SvrProcess {

	private int p_C_Invoice_ID;
	private Timestamp p_DateFrom;
	private Timestamp p_DateTo;
	/** Non-export invoice whose buyer has no Tax ID: B2C, which the IRP does not take. */
	private static final String B2C_SQL =
			"COALESCE(SupTyp,'') NOT LIKE 'EXP%' AND NOT EXISTS (SELECT 1 FROM C_BPartner bp "
			+ "WHERE bp.C_BPartner_ID=C_Invoice.C_BPartner_ID AND TRIM(COALESCE(bp.TaxID,''))<>'')";

	
	@Override
	protected void prepare() {
		for (ProcessInfoParameter p : getParameter()) {
			String name = p.getParameterName();
			if ("C_Invoice_ID".equals(name))
				p_C_Invoice_ID = p.getParameterAsInt();
			else if ("DateInvoiced".equals(name)) {
				p_DateFrom = (Timestamp) p.getParameter();
				p_DateTo = (Timestamp) p.getParameter_To();
			}
		}
		// Started from the invoice window: the open invoice wins over the parameters
		if (getTable_ID() == MInvoice.Table_ID && getRecord_ID() > 0)
			p_C_Invoice_ID = getRecord_ID();
	}

	@Override
	protected String doIt() throws Exception {

		// ---- Single invoice: errors go straight to the user -----------------------
		if (p_C_Invoice_ID > 0) {
			IrnResult r = register(p_C_Invoice_ID);
			String note = r.recovered ? " (already registered at the IRP, recovered)" : "";
			addLog(p_C_Invoice_ID, null, null, "IRN: " + r.irn + ", Ack No: " + r.ackNo + ", Ack Date: " + r.ackDt + note,
					MInvoice.Table_ID, p_C_Invoice_ID);
			return "IRN generated: " + r.irn + note;
		}

		// ---- Batch: completed sales invoices in the date range with no IRN yet ----
		if (p_DateFrom == null && p_DateTo == null)
			throw new AdempiereException("Select an invoice or a date range");

		StringBuilder where = new StringBuilder("IsSOTrx='Y' AND DocStatus IN ('CO','CL') AND Irn IS NULL "
				+ "AND COALESCE(Reversal_ID,0)=0");
		List<Object> params = new ArrayList<>();
		if (p_DateFrom != null) {
			where.append(" AND DateInvoiced>=?");
			params.add(p_DateFrom);
		}
		if (p_DateTo != null) {
			where.append(" AND DateInvoiced<=?");
			params.add(p_DateTo);
		}
		
		String base = where.toString();

		int[] ids = new Query(getCtx(), MInvoice.Table_Name, base + " AND NOT (" + B2C_SQL + ")", null)
				.setClient_ID().setParameters(params).setOrderBy("DateInvoiced, DocumentNo").getIDs();

		int skipped = new Query(getCtx(), MInvoice.Table_Name, base + " AND " + B2C_SQL, null)
				.setClient_ID().setParameters(params).count();
		String skippedNote = skipped > 0 ? ", skipped " + skipped + " B2C (no buyer GSTIN)" : "";

		if (ids.length == 0)
			return "No invoices to process" + skippedNote;

		int ok = 0, failed = 0;

		for (int id : ids) {
			try {
				IrnResult r = register(id);
				ok++;
				addLog(id, null, null, "IRN: " + r.irn + (r.recovered ? " (recovered)" : ""), MInvoice.Table_ID, id);
			} catch (Exception e) {
				failed++;
				addLog(id, null, null, "FAILED: " + e.getMessage(), MInvoice.Table_ID, id);
			}
		}
		return "Registered " + ok + ", failed " + failed + " of " + ids.length + " invoice(s)" + skippedNote;	}

	/**
	 * Registers one invoice using the IRP credentials of its organization. Each invoice
	 * commits (or fails) on its own, so one bad invoice never rolls back the others.
	 */
	private IrnResult register(int invoiceId) throws Exception {

		MSBInvoice invoice = new MSBInvoice(getCtx(), invoiceId, null);
		if (invoice.get_ID() == 0)
			throw new AdempiereException("Invoice not found: " + invoiceId);
		if (!invoice.isSOTrx())
			throw new AdempiereException(invoice.getDocumentNo() + ": e-invoice is for sales invoices only");
		if (!DocAction.STATUS_Completed.equals(invoice.getDocStatus())
				&& !DocAction.STATUS_Closed.equals(invoice.getDocStatus()))
			throw new AdempiereException(invoice.getDocumentNo() + ": invoice must be completed first");
		String existing = invoice.get_ValueAsString("Irn");
		if (existing != null && !existing.isBlank())
			throw new AdempiereException(invoice.getDocumentNo() + ": already has IRN " + existing);
		if (invoice.getReversal_ID() > 0)
			throw new AdempiereException(invoice.getDocumentNo() + ": this is a reversal document and is never sent to the IRP. "
					+ "Cancel the IRN of the original invoice instead.");
		checkReportingWindow(invoice);

		EInvoiceConfig cfg = EInvoiceConfig.load(getCtx(), invoice.getAD_Org_ID(), null);

		IrnResult result;
		try {
		    result = callIrp(cfg, invoice);
		} catch (Exception e) {
			logFailure(invoice, e);
			throw new AdempiereException(invoice.getDocumentNo() + ": " + e.getMessage(), e);
		}

		try {
			Trx.run(trxName -> {
				MSBInvoice inv = new MSBInvoice(getCtx(), invoiceId, trxName);
				saveResponse(inv, result, null, trxName);
				updateInvoiceSummary(inv, result, trxName);
			});
		} catch (Exception e) {
			// IRP already accepted it: say so loudly, the IRN must not be lost
			String msg = invoice.getDocumentNo() + ": IRP issued IRN " + result.irn
					+ " but saving it failed: " + e.getMessage();
			log.severe(msg + " | raw response: " + result.rawResponse);
			throw new AdempiereException(msg, e);
		}
		// The summary columns were written with plain SQL: drop the cached copy of the invoice
		CacheMgt.get().reset(MInvoice.Table_Name, invoiceId);
		return result;
	}
	
	/** Calls the IRP; if the cached token was rejected, logs in again and retries once. */
	private IrnResult callIrp(EInvoiceConfig cfg, MSBInvoice invoice) throws Exception {
		EInvoiceGenerateIrnService svc = new EInvoiceGenerateIrnService(cfg);
		try {
			return svc.generateIrn(invoice, EInvoiceTokenCache.get(cfg, false));
		} catch (Exception e) {
			if (!EInvoiceTokenCache.isTokenProblem(e))
				throw e;
			log.info("IRP rejected the auth token (" + e.getMessage() + "), logging in again");
			return svc.generateIrn(invoice, EInvoiceTokenCache.get(cfg, true));
		}
	}


	/** Own transaction (null), so the row survives the rollback of the caller. */
	private void logFailure(MSBInvoice inv, Exception e) {
		try {
			saveResponse(inv, null, e.getMessage(), null);
		} catch (Exception logEx) {
			log.warning("Could not log e-invoice failure: " + logEx.getMessage());
		}
	}

	private void updateInvoiceSummary(MSBInvoice inv, IrnResult r, String trx) {
		DB.executeUpdateEx(
				"UPDATE C_Invoice SET Irn=?, AckNo=?, AckDt=?, IrnStatus=?, Updated=getDate(), UpdatedBy=? "
						+ "WHERE C_Invoice_ID=?",
				new Object[] { r.irn, r.ackNo, r.ackDt, r.status, Env.getAD_User_ID(getCtx()),
						inv.getC_Invoice_ID() },
				trx);
	}

	private void saveResponse(MSBInvoice inv, IrnResult r, String error, String trx) {
		GenericPO row = new GenericPO("EI_InvoiceResponse", getCtx(), 0, trx);
		row.setAD_Org_ID(inv.getAD_Org_ID());
		row.set_ValueNoCheck("C_Invoice_ID", inv.getC_Invoice_ID());
		row.set_ValueNoCheck("IsSuccess", r != null);
		if (r != null) {
			row.set_ValueNoCheck("Irn", r.irn);
			row.set_ValueNoCheck("AckNo", r.ackNo);
			row.set_ValueNoCheck("AckDt", r.ackDt);
			row.set_ValueNoCheck("IrnStatus", r.status);
			row.set_ValueNoCheck("SignedInvoice", r.signedInvoice);
			row.set_ValueNoCheck("SignedQRCode", r.signedQrCode);
			row.set_ValueNoCheck("EwbNo", r.ewbNo);
			row.set_ValueNoCheck("EwbDt", r.ewbDt);
			row.set_ValueNoCheck("EwbValidTill", r.ewbValidTill);
			row.set_ValueNoCheck("RawResponse", r.rawResponse);
		} else {
			row.set_ValueNoCheck("ErrorMsg",
					error != null && error.length() > 2000 ? error.substring(0, 2000) : error);
		}
		row.saveEx();
	}
	
	/**
	 * Optional pre-check of the IRP's 30-day reporting limit (AATO 10 crore and above).
	 * Switched on by AD_SysConfig EI_MAX_INVOICE_AGE_DAYS (e.g. 30); 0 or missing = no check.
	 * An invoice dated 1 April can be reported up to and including 30 April.
	 */
	private void checkReportingWindow(MSBInvoice invoice) {
		int days = MSysConfig.getIntValue("EI_MAX_INVOICE_AGE_DAYS", 0, invoice.getAD_Client_ID(),
				invoice.getAD_Org_ID());
		if (days <= 0 || invoice.getDateInvoiced() == null)
			return;
		LocalDate invDate = invoice.getDateInvoiced().toLocalDateTime().toLocalDate();
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
		if (ChronoUnit.DAYS.between(invDate, today) >= days)
			throw new AdempiereException(invoice.getDocumentNo() + ": dated " + invDate + ", it can no longer be "
					+ "reported to the IRP (limit of " + days + " days, last day was " + invDate.plusDays(days - 1)
					+ "). Issue a new document, or ask your tax advisor how to handle it.");
	}
	

}