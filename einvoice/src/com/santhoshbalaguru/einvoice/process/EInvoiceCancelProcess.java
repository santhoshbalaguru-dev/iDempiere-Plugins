package com.santhoshbalaguru.einvoice.process;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.adempiere.base.annotation.Process;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.model.GenericPO;
import org.compiere.model.MInvoice;
import org.compiere.process.DocAction;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.CacheMgt;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Trx;

import com.santhoshbalaguru.einvoice.config.EInvoiceConfig;
import com.santhoshbalaguru.einvoice.model.MSBInvoice;
import com.santhoshbalaguru.einvoice.service.EInvoiceCancelIrnService;
import com.santhoshbalaguru.einvoice.service.EInvoiceCancelIrnService.CancelResult;
import com.santhoshbalaguru.einvoice.service.EInvoiceTokenCache;

@Process
public class EInvoiceCancelProcess extends SvrProcess {

	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
	private static final DateTimeFormatter ACK_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private int p_C_Invoice_ID;
	private String p_CnlRsn;
	private String p_CnlRem;

	@Override
	protected void prepare() {
		for (ProcessInfoParameter p : getParameter()) {
			String name = p.getParameterName();
			if ("C_Invoice_ID".equals(name))
				p_C_Invoice_ID = p.getParameterAsInt();
			else if ("CnlRsn".equals(name))
				p_CnlRsn = p.getParameterAsString();
			else if ("CnlRem".equals(name))
				p_CnlRem = p.getParameterAsString();
		}
		if (getTable_ID() == MInvoice.Table_ID && getRecord_ID() > 0)
			p_C_Invoice_ID = getRecord_ID();
	}

	@Override
	protected String doIt() throws Exception {
		if (p_C_Invoice_ID <= 0)
			throw new AdempiereException("Select an invoice");

		final String reason = p_CnlRsn == null ? "" : p_CnlRsn.trim();
		if (!reason.matches("[1-4]"))
			throw new AdempiereException("Cancel reason must be 1 (Duplicate), 2 (Data entry mistake), "
					+ "3 (Order cancelled) or 4 (Others)");
		final String remarks = p_CnlRem == null ? "" : p_CnlRem.trim();
		if (remarks.length() > 100 || remarks.contains("\""))
			throw new AdempiereException("Cancel remarks can be at most 100 characters and cannot contain double quotes");

		MSBInvoice invoice = new MSBInvoice(getCtx(), p_C_Invoice_ID, null);
		if (invoice.get_ID() == 0)
			throw new AdempiereException("Invoice not found: " + p_C_Invoice_ID);
		final String docNo = invoice.getDocumentNo();

		final String irn = invoice.get_ValueAsString("Irn");
		if (irn == null || irn.isBlank())
			throw new AdempiereException(docNo + ": has no IRN to cancel");
		if ("CNL".equals(invoice.get_ValueAsString("IrnStatus")))
			throw new AdempiereException(docNo + ": IRN is already cancelled");
		if (!DocAction.STATUS_Reversed.equals(invoice.getDocStatus()))
			throw new AdempiereException(docNo + ": reverse the invoice first (current status: "
					+ invoice.getDocStatus() + "), then cancel its IRN");
		checkWindow(docNo, invoice.get_ValueAsString("AckDt"));

		EInvoiceConfig cfg = EInvoiceConfig.load(getCtx(), invoice.getAD_Org_ID(), null);

		CancelResult r;
		try {
			r = cancel(cfg, irn, reason, remarks);
		} catch (Exception e) {
			logFailure(invoice, irn, e);
			throw new AdempiereException(docNo + ": " + e.getMessage(), e);
		}

		final CancelResult result = r;
		try {
			Trx.run(trxName -> {
				MSBInvoice inv = new MSBInvoice(getCtx(), p_C_Invoice_ID, trxName);
				saveResponse(inv, result, irn, null, trxName);
				DB.executeUpdateEx("UPDATE C_Invoice SET IrnStatus='CNL', Updated=getDate(), UpdatedBy=? "
						+ "WHERE C_Invoice_ID=?",
						new Object[] { Env.getAD_User_ID(getCtx()), p_C_Invoice_ID }, trxName);
			});
		} catch (Exception e) {
			// The IRP already cancelled it: say so loudly, the local status must be corrected
			String msg = docNo + ": IRP cancelled IRN " + irn + " but saving the status failed: " + e.getMessage();
			log.severe(msg + " | raw response: " + result.rawResponse);
			throw new AdempiereException(msg, e);
		}
		
		CacheMgt.get().reset(MInvoice.Table_Name, p_C_Invoice_ID);

		String note = "IRN cancelled: " + result.irn + (result.cancelDate != null ? " (" + result.cancelDate + ")" : "");
		addLog(p_C_Invoice_ID, null, null, note, MInvoice.Table_ID, p_C_Invoice_ID);
		return note;
	}
	
	private CancelResult cancel(EInvoiceConfig cfg, String irn, String reason, String remarks) throws Exception {
		EInvoiceCancelIrnService svc = new EInvoiceCancelIrnService(cfg);
		try {
			return svc.cancelIrn(irn, reason, remarks, EInvoiceTokenCache.get(cfg, false));
		} catch (Exception e) {
			if (!EInvoiceTokenCache.isTokenProblem(e))
				throw e;
			return svc.cancelIrn(irn, reason, remarks, EInvoiceTokenCache.get(cfg, true));
		}
	}

	/** The IRP enforces 24 hours from the acknowledgement; this gives a clear message first. */
	private void checkWindow(String docNo, String ackDt) {
		if (ackDt == null || ackDt.isBlank())
			return;
		try {
			ZonedDateTime registered = LocalDateTime.parse(ackDt.trim(), ACK_FMT).atZone(IST);
			if (ZonedDateTime.now(IST).isAfter(registered.plusHours(24)))
				throw new AdempiereException(docNo + ": the IRN was generated at " + ackDt
						+ " (IST) and can only be cancelled within 24 hours. Issue a credit note instead.");
		} catch (DateTimeParseException e) {
			// unreadable date: let the IRP decide
		}
	}

	private void logFailure(MSBInvoice inv, String irn, Exception e) {
		try {
			saveResponse(inv, null, irn, "Cancel failed: " + e.getMessage(), null);
		} catch (Exception logEx) {
			log.warning("Could not log e-invoice cancel failure: " + logEx.getMessage());
		}
	}

	private void saveResponse(MSBInvoice inv, CancelResult r, String irn, String error, String trx) {
		GenericPO row = new GenericPO("EI_InvoiceResponse", getCtx(), 0, trx);
		row.setAD_Org_ID(inv.getAD_Org_ID());
		row.set_ValueNoCheck("C_Invoice_ID", inv.getC_Invoice_ID());
		row.set_ValueNoCheck("IsSuccess", r != null);
		row.set_ValueNoCheck("Irn", irn);
		if (r != null) {
			row.set_ValueNoCheck("IrnStatus", "CNL");
			row.set_ValueNoCheck("RawResponse", r.rawResponse);
		} else {
			row.set_ValueNoCheck("ErrorMsg", error != null && error.length() > 2000 ? error.substring(0, 2000) : error);
		}
		row.saveEx();
	}
}