package com.santhoshbalaguru.einvoice.model;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Properties;

import org.compiere.model.MInvoice;
import org.compiere.util.Env;

/**
 * C_Invoice wrapper that keeps all standard MInvoice behaviour and adds
 * typed accessors for the e-invoice custom columns.
 */
public class MSBInvoice extends MInvoice {

	private static final long serialVersionUID = 20260928L;

	// Custom column names on C_Invoice
	public static final String COLUMNNAME_SupTyp = "SupTyp";
	public static final String COLUMNNAME_RegRev = "RegRev";
	public static final String COLUMNNAME_EcmGstin = "EcmGstin";
	public static final String COLUMNNAME_IgstOnIntra = "IgstOnIntra";
	public static final String COLUMNNAME_InvNo = "InvNo";
	public static final String COLUMNNAME_OthRefNo = "OthRefNo";
	public static final String COLUMNNAME_InvEndDt = "InvEndDt";
	public static final String COLUMNNAME_ContrRefr = "ContrRefr";
	public static final String COLUMNNAME_TendRefr = "TendRefr";
	public static final String COLUMNNAME_ExtRefr = "ExtRefr";
	public static final String COLUMNNAME_PayInstr = "PayInstr";
	public static final String COLUMNNAME_CrTrn = "CrTrn";
	public static final String COLUMNNAME_DirDr = "DirDr";
	public static final String COLUMNNAME_RefClm = "RefClm";
	public static final String COLUMNNAME_ExpDuty = "ExpDuty";
	public static final String COLUMNNAME_InvStDt = "InvStDt";
	public static final String COLUMNNAME_ShipBNo = "ShipBNo";
	public static final String COLUMNNAME_ShipBDt = "ShipBDt";

	// SupTyp list reference values
	public static final String SUPTYP_B2B = "B2B";
	public static final String SUPTYP_SEZWP = "SEZWP";
	public static final String SUPTYP_SEZWOP = "SEZWOP";
	public static final String SUPTYP_EXPWP = "EXPWP";
	public static final String SUPTYP_EXPWOP = "EXPWOP";
	public static final String SUPTYP_DEXP = "DEXP";

	public MSBInvoice(Properties ctx, int C_Invoice_ID, String trxName) {
		super(ctx, C_Invoice_ID, trxName);
	}

	public MSBInvoice(Properties ctx, ResultSet rs, String trxName) {
		super(ctx, rs, trxName);
	}

	/** Reload an existing MInvoice as MSBInvoice. */
	public MSBInvoice(MInvoice invoice) {
		super(invoice.getCtx(), invoice.getC_Invoice_ID(), invoice.get_TrxName());
	}

	/** Maps the SupTyp list value to the code the IRP expects (B2B, SEZWP...). */
	public String getSupTypCode() {
		String v = getSupTyp();
		return (v == null || v.isBlank()) ? null : v.trim().toUpperCase();
	}

	// ---- String columns ----------------------------------------------------

	public String getSupTyp() {
		return (String) get_Value(COLUMNNAME_SupTyp);
	}

	public void setSupTyp(String v) {
		set_Value(COLUMNNAME_SupTyp, v);
	}

	public String getEcmGstin() {
		return (String) get_Value(COLUMNNAME_EcmGstin);
	}

	public void setEcmGstin(String v) {
		set_Value(COLUMNNAME_EcmGstin, v);
	}

	public String getInvNo() {
		return (String) get_Value(COLUMNNAME_InvNo);
	}

	public void setInvNo(String v) {
		set_Value(COLUMNNAME_InvNo, v);
	}

	public String getOthRefNo() {
		return (String) get_Value(COLUMNNAME_OthRefNo);
	}

	public void setOthRefNo(String v) {
		set_Value(COLUMNNAME_OthRefNo, v);
	}

	public String getContrRefr() {
		return (String) get_Value(COLUMNNAME_ContrRefr);
	}

	public void setContrRefr(String v) {
		set_Value(COLUMNNAME_ContrRefr, v);
	}

	public String getTendRefr() {
		return (String) get_Value(COLUMNNAME_TendRefr);
	}

	public void setTendRefr(String v) {
		set_Value(COLUMNNAME_TendRefr, v);
	}

	public String getExtRefr() {
		return (String) get_Value(COLUMNNAME_ExtRefr);
	}

	public void setExtRefr(String v) {
		set_Value(COLUMNNAME_ExtRefr, v);
	}

	public String getPayInstr() {
		return (String) get_Value(COLUMNNAME_PayInstr);
	}

	public void setPayInstr(String v) {
		set_Value(COLUMNNAME_PayInstr, v);
	}

	public String getCrTrn() {
		return (String) get_Value(COLUMNNAME_CrTrn);
	}

	public void setCrTrn(String v) {
		set_Value(COLUMNNAME_CrTrn, v);
	}

	public String getDirDr() {
		return (String) get_Value(COLUMNNAME_DirDr);
	}

	public void setDirDr(String v) {
		set_Value(COLUMNNAME_DirDr, v);
	}
	
	public String getShipBNo() {
		return (String) get_Value(COLUMNNAME_ShipBNo);
	}

	public void setShipBNo(String v) {
		set_Value(COLUMNNAME_ShipBNo, v);
	}


	// ---- Boolean columns ---------------------------------------------------

	public boolean isRegRev() {
		return get_ValueAsBoolean(COLUMNNAME_RegRev);
	}

	public void setRegRev(boolean v) {
		set_Value(COLUMNNAME_RegRev, Boolean.valueOf(v));
	}

	public boolean isIgstOnIntra() {
		return get_ValueAsBoolean(COLUMNNAME_IgstOnIntra);
	}

	public void setIgstOnIntra(boolean v) {
		set_Value(COLUMNNAME_IgstOnIntra, Boolean.valueOf(v));
	}

	public boolean isRefClm() {
		return get_ValueAsBoolean(COLUMNNAME_RefClm);
	}

	public void setRefClm(boolean v) {
		set_Value(COLUMNNAME_RefClm, Boolean.valueOf(v));
	}

	// ---- Date / number columns --------------------------------------------

	public Timestamp getInvEndDt() {
		return (Timestamp) get_Value(COLUMNNAME_InvEndDt);
	}

	public void setInvEndDt(Timestamp v) {
		set_Value(COLUMNNAME_InvEndDt, v);
	}

	public BigDecimal getExpDuty() {
		BigDecimal bd = (BigDecimal) get_Value(COLUMNNAME_ExpDuty);
		return bd == null ? Env.ZERO : bd;
	}

	public void setExpDuty(BigDecimal v) {
		set_Value(COLUMNNAME_ExpDuty, v);
	}
	
	public Timestamp getInvStDt() {
		return (Timestamp) get_Value(COLUMNNAME_InvStDt);
	}

	public void setInvStDt(Timestamp v) {
		set_Value(COLUMNNAME_InvStDt, v);
	}

	public Timestamp getShipBDt() {
		return (Timestamp) get_Value(COLUMNNAME_ShipBDt);
	}

	public void setShipBDt(Timestamp v) {
		set_Value(COLUMNNAME_ShipBDt, v);
	}
}