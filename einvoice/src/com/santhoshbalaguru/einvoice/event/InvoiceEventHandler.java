package com.santhoshbalaguru.einvoice.event;

import org.adempiere.base.event.AbstractEventHandler;
import org.adempiere.base.event.IEventTopics;
import org.compiere.model.MInvoice;
import org.compiere.model.PO;
import org.osgi.service.event.Event;

import com.santhoshbalaguru.einvoice.util.IrnWindow;

/**
 * Protects sales invoices that have an active IRN:
 *  - Void / Reverse-Correct / Reverse-Accrual: allowed only within 24 hours of the IRN
 *    (the IRN must then be cancelled); later, a credit memo is the only valid route.
 *  - Reactivate: never allowed, the invoice would no longer match what the IRP holds.
 */
public class InvoiceEventHandler extends AbstractEventHandler {

	@Override
	protected void initialize() {
		registerTableEvent(IEventTopics.DOC_BEFORE_VOID, MInvoice.Table_Name);
		registerTableEvent(IEventTopics.DOC_BEFORE_REVERSECORRECT, MInvoice.Table_Name);
		registerTableEvent(IEventTopics.DOC_BEFORE_REVERSEACCRUAL, MInvoice.Table_Name);
		registerTableEvent(IEventTopics.DOC_BEFORE_REACTIVATE, MInvoice.Table_Name);
	}

	@Override
	protected void doHandleEvent(Event event) {
		PO po = getPO(event);
		if (!(po instanceof MInvoice))
			return;
		MInvoice invoice = (MInvoice) po;
		if (!invoice.isSOTrx())
			return;

		String irn = invoice.get_ValueAsString("Irn");
		if (irn == null || irn.isBlank() || "CNL".equals(invoice.get_ValueAsString("IrnStatus")))
			return; // no IRN, or already cancelled: nothing to protect

		String topic = event.getTopic();
		if (IEventTopics.DOC_BEFORE_REACTIVATE.equals(topic)) {
			addErrorMessage(event, invoice.getDocumentNo() + " has an active IRN (" + irn
					+ ") and cannot be reactivated. Reverse it within 24 hours and cancel the IRN, "
					+ "or issue a credit memo.");
			return;
		}

		// Void / reverse
		String ackDt = invoice.get_ValueAsString("AckDt");
		if (!IrnWindow.isWithin24Hours(ackDt))
			addErrorMessage(event, invoice.getDocumentNo() + " has an active IRN generated at " + ackDt
					+ " (IST), more than 24 hours ago, so it can no longer be cancelled at the IRP. "
					+ "Issue a credit memo instead of reversing it.");
	}
}