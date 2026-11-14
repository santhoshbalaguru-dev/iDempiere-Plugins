package com.santhoshbalaguru.paymentlink.process;

import org.adempiere.base.annotation.Process;
import org.compiere.model.MInvoice;
import org.compiere.process.SvrProcess;

import com.santhoshbalaguru.paymentlink.sync.PaymentLinkSync;

/**
 * Sync Payment Links: see {@link PaymentLinkSync}. No parameters; safe to run repeatedly or on a schedule.
 */
@Process
public class SyncPaymentLinks extends SvrProcess {

	@Override
	protected void prepare() {
		// no parameters
	}

	@Override
	protected String doIt() throws Exception {
		return new PaymentLinkSync(getCtx(), getAD_Client_ID(), get_TrxName(),
				(invoiceId, msg) -> addLog(invoiceId, null, null, msg, MInvoice.Table_ID, invoiceId)).run().summary();
	}
}