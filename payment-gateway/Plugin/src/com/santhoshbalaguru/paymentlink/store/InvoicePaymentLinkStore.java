package com.santhoshbalaguru.paymentlink.store;

import java.sql.Timestamp;
import java.util.List;
import java.util.Properties;

import org.compiere.model.MTable;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.util.DB;

import com.santhoshbalaguru.paymentlink.client.PaymentServiceClient.LinkResponse;

/**
 * Reads and writes PL_InvoicePaymentLink (generic PO, no model class, same style as EI_Config)
 * and keeps C_Invoice.PaymentLinkURL pointing at the current link.
 */
public final class InvoicePaymentLinkStore {

	public static final String TABLE = "PL_InvoicePaymentLink";

	private InvoicePaymentLinkStore() {
	}

	/** True when the invoice has a CREATED link that has not expired yet. */
	public static boolean hasActiveLink(Properties ctx, int invoiceId, String trx) {
		return new Query(ctx, TABLE, "C_Invoice_ID=? AND LinkStatus='CREATED' AND (ExpireBy IS NULL OR ExpireBy>?)", trx)
				.setClient_ID().setOnlyActiveRecords(true)
				.setParameters(invoiceId, new Timestamp(System.currentTimeMillis())).match();
	}

	/**
	 * Records the link the service returned: updates the row for the same service link when it
	 * already exists (the service reused it), otherwise inserts one. Any other CREATED link on the
	 * invoice is marked CANCELLED, because the service cancels the old link when the amount changes.
	 * Finally C_Invoice.PaymentLinkURL is set to the current link.
	 */
	public static PO save(Properties ctx, int orgId, int invoiceId, int currencyId, LinkResponse link, String trx) {
		PO row = new Query(ctx, TABLE, "Gateway=? AND ServiceLinkNo=?", trx).setClient_ID()
				.setParameters(link.gateway, Math.toIntExact(link.serviceLinkNo)).first();
		if (row == null) {
			row = MTable.get(ctx, TABLE).getPO(0, trx);
			row.setAD_Org_ID(orgId);
			row.set_ValueOfColumn("C_Invoice_ID", invoiceId);
			row.set_ValueOfColumn("Gateway", link.gateway);
			row.set_ValueOfColumn("ServiceLinkNo", Math.toIntExact(link.serviceLinkNo));
		}
		row.set_ValueOfColumn("GatewayLinkRef", link.gatewayLinkRef);
		row.set_ValueOfColumn("LinkURL", link.shortUrl);
		row.set_ValueOfColumn("Amount", link.amount);
		row.set_ValueOfColumn("C_Currency_ID", currencyId);
		row.set_ValueOfColumn("ExpireBy", link.expireBy == null ? null : Timestamp.from(link.expireBy.toInstant()));
		row.set_ValueOfColumn("LinkStatus", "CREATED");
		row.set_ValueOfColumn("ErrorMessage", null);
		row.saveEx();

		List<PO> others = new Query(ctx, TABLE,
				"C_Invoice_ID=? AND LinkStatus='CREATED' AND " + TABLE + "_ID<>?", trx).setClient_ID()
				.setParameters(invoiceId, row.get_ID()).list();
		for (PO other : others) {
			Timestamp exp = (Timestamp) other.get_Value("ExpireBy");
			boolean expired = exp != null && exp.before(new Timestamp(System.currentTimeMillis()));
			other.set_ValueOfColumn("LinkStatus", expired ? "EXPIRED" : "CANCELLED");
			other.saveEx();
		}

		DB.executeUpdateEx("UPDATE C_Invoice SET PaymentLinkURL=? WHERE C_Invoice_ID=?",
				new Object[] { link.shortUrl, invoiceId }, trx);
		return row;
	}
}
