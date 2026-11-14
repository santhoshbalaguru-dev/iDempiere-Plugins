package com.santhoshbalaguru.einvoice.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MAttributeSetInstance;
import org.compiere.model.MBPartner;
import org.compiere.model.MBPartnerLocation;
import org.compiere.model.MCharge;
import org.compiere.model.MCity;
import org.compiere.model.MCountry;
import org.compiere.model.MCurrency;
import org.compiere.model.MDocType;
import org.compiere.model.MInOut;
import org.compiere.model.MInOutLine;
import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MLocation;
import org.compiere.model.MOrder;
import org.compiere.model.MOrg;
import org.compiere.model.MOrgInfo;
import org.compiere.model.MPaymentTerm;
import org.compiere.model.MProduct;
import org.compiere.model.MRegion;
import org.compiere.model.MTax;
import org.compiere.model.MUOM;
import org.compiere.model.MUser;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.util.DB;
import org.compiere.model.MConversionRate;


import com.santhoshbalaguru.einvoice.model.MSBInvoice;
import com.santhoshbalaguru.einvoice.model.generateirn.request.BchDtls;
import com.santhoshbalaguru.einvoice.model.generateirn.request.BuyerDtls;
import com.santhoshbalaguru.einvoice.model.generateirn.request.ContrDtls;
import com.santhoshbalaguru.einvoice.model.generateirn.request.DocDtls;
import com.santhoshbalaguru.einvoice.model.generateirn.request.DocPerdDtls;
import com.santhoshbalaguru.einvoice.model.generateirn.request.EwbDtls;
import com.santhoshbalaguru.einvoice.model.generateirn.request.ExpDtls;
import com.santhoshbalaguru.einvoice.model.generateirn.request.InvoiceRequest;
import com.santhoshbalaguru.einvoice.model.generateirn.request.ItemList;
import com.santhoshbalaguru.einvoice.model.generateirn.request.PayDtls;
import com.santhoshbalaguru.einvoice.model.generateirn.request.PrecDocDtls;
import com.santhoshbalaguru.einvoice.model.generateirn.request.RefDtls;
import com.santhoshbalaguru.einvoice.model.generateirn.request.SellerDtls;
import com.santhoshbalaguru.einvoice.model.generateirn.request.ShipDtls;
import com.santhoshbalaguru.einvoice.model.generateirn.request.TranDtls;
import com.santhoshbalaguru.einvoice.model.generateirn.request.ValDtls;
import com.santhoshbalaguru.einvoice.model.generateirn.request.IrpPatterns;

/**
 * Maps a completed iDempiere sales invoice (MSBInvoice) to the IRP
 * GENERATE request payload.
 *
 * Assumptions (see notes in the chat reply):
 *  - Seller GSTIN is AD_OrgInfo.TaxID, buyer GSTIN is C_BPartner.TaxID
 *  - HSN/SAC is a custom column "HsnCd" on M_Product (and C_Charge for charge lines)
 *  - GST taxes are either summary taxes with CGST/SGST/IGST/CESS child taxes
 *    (identified by name) or a single GST rate that is split by place of supply
 *  - Amounts are in INR
 */
public class EInvoiceRequestMapper {

	private static final DateTimeFormatter IRP_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
	private static final BigDecimal HUNDRED = new BigDecimal("100");
	
	/** DocBaseType the customer adds to the "C_DocType DocBaseType" list for sales debit notes (3 characters max). */
	private static final String DOCBASETYPE_ARDebitNote = "ARD";

	private static final Map<String, String> UOM_CODES = new HashMap<>();

	static {

		String[][] uoms = { { "EA", "NOS" }, { "NO", "NOS" }, { "PC", "PCS" }, { "PCE", "PCS" }, { "BX", "BOX" },
				{ "KG", "KGS" }, { "GM", "GMS" }, { "LT", "LTR" }, { "MR", "MTR" }, { "ST", "SET" },
				{ "DZ", "DOZ" }, { "PR", "PRS" }, { "TN", "TON" }, { "PK", "PAC" }, { "RL", "ROL" },
				{ "BG", "BAG" }, { "BT", "BTL" }, { "CT", "CTN" }, { "CN", "CAN" }, { "SM", "SQM" },
				{ "SF", "SQF" }, { "TU", "TUB" }, { "UN", "UNT" } };
		for (String[] u : uoms)
			UOM_CODES.put(u[0], u[1]);
	}

	/** Running totals for the document value section. */
	private static class Totals {
		BigDecimal ass = ZERO, cgst = ZERO, sgst = ZERO, igst = ZERO, ces = ZERO;
	}

	public InvoiceRequest mapInvoiceRequest(MSBInvoice invoice) {

		String supTyp = invoice.getSupTypCode();
		if (supTyp == null)
			supTyp = "B2B";
		boolean export = supTyp.startsWith("EXP");
		int inrId = inrCurrencyId();
		boolean foreign = invoice.getC_Currency_ID() != inrId;
		BigDecimal rate = foreign ? inrRate(invoice, inrId) : BigDecimal.ONE;

		// ---- Transaction / document ------------------------------------------------
		TranDtls tranDtls = new TranDtls();
		tranDtls.setTaxSch("GST");
		tranDtls.setSupTyp(supTyp);
		tranDtls.setRegRev(yn(invoice.isRegRev()));
		tranDtls.setEcmGstin(blank(invoice.getEcmGstin()));
		tranDtls.setIgstOnIntra(yn(invoice.isIgstOnIntra()));
		
		if (invoice.isIgstOnIntra() && !invoice.isRegRev())
		    throw new AdempiereException("IGST on intra-state supply requires Reverse Charge to be set (IRP validation 32).");

		DocDtls docDtls = buildDocDtls(invoice);

		// ---- Parties ---------------------------------------------------------------
		SellerDtls seller = buildSeller(invoice);
		BuyerDtls buyer = buildBuyer(invoice, export);
		ShipDtls ship = buildShip(invoice, buyer);

		// Exports and SEZ supplies are always inter-state (IRP validation 34), so IGST applies
		// even when seller and buyer are in the same state. Otherwise it follows place of supply,
		// unless the invoice is flagged as IGST on intra-state.
		boolean sez = supTyp.startsWith("SEZ");
		boolean igstApplies = invoice.isIgstOnIntra() || export || sez
		        || !seller.getStcd().equals(buyer.getPos());
		// ---- Items -----------------------------------------------------------------
		Totals totals = new Totals();
		List<ItemList> items = buildItems(invoice, igstApplies, totals, rate);
		if (items.isEmpty())
			throw new AdempiereException("Invoice has no product or charge lines to send to the IRP");

		// ---- Values (all in INR) ---------------------------------------------------
		BigDecimal grandFc = scale2(invoice.getGrandTotal());   // invoice currency
		BigDecimal computed = totals.ass.add(totals.cgst).add(totals.sgst).add(totals.igst).add(totals.ces);
		BigDecimal grand, rndOff;
		if (foreign) {
			// Converted amounts carry their own rounding, so the INR total is the sum of the INR lines
			grand = computed;
			rndOff = ZERO;
		} else {
			grand = grandFc;
			rndOff = grand.subtract(computed);
			if (rndOff.abs().compareTo(BigDecimal.ONE) > 0)
				throw new AdempiereException("Invoice total " + grand + " differs from the sum of line values "
						+ computed + " by more than 1.00. Check the tax setup (CGST/SGST/IGST child taxes).");
		}

		ValDtls valDtls = new ValDtls();
		valDtls.setAssVal(totals.ass);
		valDtls.setCgstVal(totals.cgst);
		valDtls.setSgstVal(totals.sgst);
		valDtls.setIgstVal(totals.igst);
		valDtls.setCesVal(totals.ces);
		valDtls.setStCesVal(ZERO);
		valDtls.setDiscount(ZERO);
		valDtls.setOthChrg(ZERO);
		valDtls.setRndOffAmt(rndOff);
		valDtls.setTotInvVal(grand);
		if (foreign)
			valDtls.setTotInvValFc(grandFc);   // the only place the foreign amount appears

		// ---- Payment / reference / export -----------------------------------------
		PayDtls payDtls = buildPayDtls(invoice, grand, rate);
		RefDtls refDtls = buildRefDtls(invoice);
		ExpDtls expDtls = export ? buildExpDtls(invoice, rate) : null;

		// ---- Final request ---------------------------------------------------------
		InvoiceRequest request = new InvoiceRequest();
		request.setVersion("1.1");
		request.setTranDtls(tranDtls);
		request.setDocDtls(docDtls);
		request.setSellerDtls(seller);
		request.setBuyerDtls(buyer);
		request.setShipDtls(ship);
		request.setItemList(items);
		request.setValDtls(valDtls);
		request.setPayDtls(payDtls);
		request.setRefDtls(refDtls);
		request.setExpDtls(expDtls);
		// No e-way bill for exports, credit/debit notes, or invoices with services only
		// (the IRP generates one only when at least one item is goods)
		String docTyp = docDtls.getTyp();
		boolean noteDoc = "CRN".equals(docTyp) || "DBN".equals(docTyp);
		boolean servicesOnly = items.stream().allMatch(i -> "Y".equals(i.getIsServc()));
		request.setEwbDtls(export || noteDoc || servicesOnly ? null : buildEwbDtls(invoice));
		// DispDtls and AddlDocDtls are optional and not sourced from the invoice yet
		return request;
	}

	// =====================================================================
	// DOCUMENT
	// =====================================================================

	private DocDtls buildDocDtls(MSBInvoice invoice) {
	    MDocType dt = MDocType.get(invoice.getC_DocType_ID());
	    String base = dt == null ? null : dt.getDocBaseType();

	    String typ;
	    if (MDocType.DOCBASETYPE_ARInvoice.equals(base))
	        typ = "INV";
	    else if (MDocType.DOCBASETYPE_ARCreditMemo.equals(base))
	        typ = "CRN";
	    else if (DOCBASETYPE_ARDebitNote.equals(base))
	        typ = "DBN";
	    else
	        throw new AdempiereException("Document base type '" + base + "' of the document type has no IRP "
	                + "document type. Supported: AR Invoice (INV), AR Credit Memo (CRN), " + DOCBASETYPE_ARDebitNote + " (DBN).");

		String no = invoice.getDocumentNo();
		if (no == null || no.trim().isEmpty() || no.length() > 16)
			throw new AdempiereException("IRP document number must be 1-16 characters, got: " + no);

		DocDtls d = new DocDtls();
		d.setTyp(typ);
		d.setNo(no);
		d.setDt(date(invoice.getDateInvoiced()));
		return d;
	}

	// =====================================================================
	// PARTIES
	// =====================================================================

	private SellerDtls buildSeller(MSBInvoice invoice) {
		Properties ctx = invoice.getCtx();
		String trx = invoice.get_TrxName();

		MOrgInfo oi = MOrgInfo.get(ctx, invoice.getAD_Org_ID(), trx);
		MOrg org = MOrg.get(ctx, invoice.getAD_Org_ID());

		String gstin = blank(oi.getTaxID());
		if (gstin == null)
			throw new AdempiereException("Seller GSTIN missing: set Tax ID on the organization info");
		if (oi.getC_Location_ID() <= 0)
			throw new AdempiereException("Seller address missing: set the location on the organization info");

		MLocation loc = new MLocation(ctx, oi.getC_Location_ID(), trx);
		
		SellerDtls s = new SellerDtls();
		s.setGstin(gstin);
		s.setLglNm(trunc(org.getName(), 100));
		String trd = opt(oi.get_ValueAsString("TrdNm"), 3, 100);
		s.setTrdNm(trd != null ? trd : trunc(org.getName(), 100));
		s.setAddr1(addr1(loc, "Seller"));
		s.setAddr2(addr2(loc));
		s.setLoc(city(loc, "Seller"));     
		s.setPin(pin(loc, "Seller"));
		s.setStcd(sellerStateCode(gstin, loc));
		s.setPh(phone(oi.get_ValueAsString("Phone")));
		s.setEm(email(oi.get_ValueAsString("EMail")));
		return s;
	}

	private BuyerDtls buildBuyer(MSBInvoice invoice, boolean export) {
		Properties ctx = invoice.getCtx();
		String trx = invoice.get_TrxName();

		MBPartner bp = new MBPartner(ctx, invoice.getC_BPartner_ID(), trx);
		MBPartnerLocation bpl = new MBPartnerLocation(ctx, invoice.getC_BPartner_Location_ID(), trx);
		MLocation loc = new MLocation(ctx, bpl.getC_Location_ID(), trx);
		MUser user = invoice.getAD_User_ID() > 0 ? new MUser(ctx, invoice.getAD_User_ID(), trx) : null;

		String gstin = blank(bp.getTaxID());

		BuyerDtls b = new BuyerDtls();
		b.setLglNm(trunc(bp.getName(), 100));
		String trd = opt(bp.getName2(), 3, 100);
		b.setTrdNm(trd != null ? trd : opt(bp.getName(), 3, 100));
		b.setAddr1(addr1(loc, "Buyer"));
		b.setAddr2(addr2(loc));
		b.setLoc(city(loc, "Buyer"));
		if (export) {
			b.setGstin("URP");
			b.setStcd("96");
			b.setPos("96");
			b.setPin(999999);
		} else {
		    if (gstin == null)
		        throw new AdempiereException("Buyer GSTIN missing for " + bp.getName()
		                + ": the IRP accepts URP only for export supply types (EXPWP / EXPWOP). "
		                + "Set the Tax ID on the business partner, or change the invoice's Supply Type.");
		    String gst = gstin.toUpperCase();
		    String stcd = buyerStateCode(gst, loc, bp.getName());
		    b.setGstin(gst);
		    b.setStcd(stcd);
		    b.setPos(stcd);   // place of supply = bill-to state, also for bill-to/ship-to supplies
		    b.setPin(pin(loc, "Buyer"));
		}

		String ph = phone(user != null ? user.getPhone() : null);
		if (ph == null)
			ph = phone(bpl.getPhone());
		b.setPh(ph);
		b.setEm(user != null ? email(user.getEMail()) : null);
		return b;
	}

	/** Ship-to is only sent when the order ships to a different location than the bill-to. */
	private ShipDtls buildShip(MSBInvoice invoice, BuyerDtls buyer) {
		if (invoice.getC_Order_ID() <= 0)
			return null;

		Properties ctx = invoice.getCtx();
		String trx = invoice.get_TrxName();

		MOrder order = new MOrder(ctx, invoice.getC_Order_ID(), trx);
		int shipLocId = order.getC_BPartner_Location_ID();
		if (shipLocId <= 0 || shipLocId == invoice.getC_BPartner_Location_ID())
			return null;

		MBPartnerLocation bpl = new MBPartnerLocation(ctx, shipLocId, trx);
		if (bpl.getC_Location_ID() <= 0)
			return null;
		MLocation loc = new MLocation(ctx, bpl.getC_Location_ID(), trx);
		MBPartner bp = new MBPartner(ctx, order.getC_BPartner_ID(), trx);

		String regionCode = stateFromRegion(loc);
		// No usable region: fall back to the buyer's state, but never to 96 (export buyer),
		// because the ship-to of an export is an Indian port
		String stcd = regionCode != null ? regionCode : buyer.getStcd();
		if ("96".equals(stcd))
			throw new AdempiereException("Ship-to state could not be determined: set a region (state) on the ship-to "
					+ "address. For exports it must be the Indian state of the port the goods leave from.");

		// A GSTIN belongs to one state. The buyer's GSTIN is only valid here if the ship-to is in the same state.
		String gstin = buyer.getGstin();
		boolean sameState = gstin != null && !"URP".equals(gstin) && gstin.startsWith(stcd.length() == 1 ? "0" + stcd : stcd);

		ShipDtls s = new ShipDtls();
		s.setGstin(sameState ? gstin : null);
		s.setLglNm(trunc(bp.getName(), 100));
		s.setTrdNm(opt(bp.getName(), 3, 100));
		s.setAddr1(addr1(loc, "Ship-to"));
		s.setAddr2(addr2(loc));
		s.setLoc(city(loc, "Ship-to"));
		s.setPin(pin(loc, "Ship-to"));
		s.setStcd(stcd);
		return s;
	}

	// =====================================================================
	// ITEMS
	// =====================================================================

	private List<ItemList> buildItems(MSBInvoice invoice, boolean igstApplies, Totals totals, BigDecimal rate) {
		Properties ctx = invoice.getCtx();
		String trx = invoice.get_TrxName();

		// Negative lines (discount charges) cannot be items: the IRP has no negative amounts.
		// They are deducted as Discount from the positive lines with the same tax.
		List<MInvoiceLine> lines = new ArrayList<>();
		Map<Integer, BigDecimal> credit = new HashMap<>();   // C_Tax_ID -> amount still to deduct
		for (MInvoiceLine l : invoice.getLines()) {
			if (l.getM_Product_ID() <= 0 && l.getC_Charge_ID() <= 0)
				continue; // text-only line
			BigDecimal b = taxableValue(invoice, l, rate);
			if (b.signum() < 0)
				credit.merge(l.getC_Tax_ID(), b.negate(), BigDecimal::add);
			else
				lines.add(l);
		}

		List<ItemList> items = new ArrayList<>();
		int sl = 0;

		for (MInvoiceLine line : lines) {

			boolean isProduct = line.getM_Product_ID() > 0;
			MProduct product = isProduct ? line.getProduct() : null;
			MCharge charge = isProduct ? null : MCharge.get(ctx, line.getC_Charge_ID());
			PO source = isProduct ? product : charge;
			String name = isProduct ? product.getName() : charge.getName();

			String hsn = blank(source.get_ValueAsString("HsnCd"));
			if (hsn == null)
				throw new AdempiereException("HSN/SAC code (HsnCd) missing for: " + name);

			BigDecimal qty = line.getQtyInvoiced().setScale(3, RoundingMode.HALF_UP);
			BigDecimal base = taxableValue(invoice, line, rate);

			// Deduct what is left of the negative lines carrying the same tax
			BigDecimal left = credit.getOrDefault(line.getC_Tax_ID(), BigDecimal.ZERO);
			BigDecimal disc = ZERO;
			if (left.signum() > 0) {
				disc = left.min(base);
				credit.put(line.getC_Tax_ID(), left.subtract(disc));
			}
			BigDecimal ass = base.subtract(disc);

			BigDecimal unitPrice = qty.signum() == 0 ? ZERO
					: base.divide(qty, 3, RoundingMode.HALF_UP);

			// Products follow their product type. Charges: SAC codes start with 99, anything else is goods
			boolean service = isProduct ? MProduct.PRODUCTTYPE_Service.equals(product.getProductType())
					: hsn.startsWith("99");

			ItemList item = new ItemList();
			item.setSlNo(String.valueOf(++sl));
			item.setPrdDesc(opt(name, 3, 300));
			item.setIsServc(service ? "Y" : "N");
			item.setHsnCd(hsn);
			if (isProduct) {
				item.setBarcde(opt(product.getUPC(), 3, 30));
				item.setOrgCntry(originCountry(ctx, product));
			}

			item.setQty(qty);
			item.setFreeQty(freeQty(line));
			item.setUnit(uomCode(line));
			item.setUnitPrice(unitPrice);
			item.setTotAmt(base);
			item.setDiscount(disc);
			item.setPreTaxVal(ass);
			item.setAssAmt(ass);

			applyTax(item, line, ass, igstApplies);

			item.setStateCesRt(ZERO);
			item.setStateCesAmt(ZERO);
			item.setStateCesNonAdvlAmt(ZERO);
			item.setOthChrg(ZERO);

			BigDecimal totItem = ass.add(item.getCgstAmt()).add(item.getSgstAmt()).add(item.getIgstAmt())
					.add(item.getCesAmt());
			item.setTotItemVal(totItem);

			// Batch (only when the line carries a lot)
			if (line.getM_AttributeSetInstance_ID() > 0) {
				MAttributeSetInstance asi = new MAttributeSetInstance(ctx, line.getM_AttributeSetInstance_ID(), trx);
				String lot = opt(asi.getLot(), 3, 20);
				if (lot != null) {
				    BchDtls bch = new BchDtls();
				    bch.setNm(lot);
					if (asi.getGuaranteeDate() != null)
						bch.setExpDt(date(asi.getGuaranteeDate()));
					bch.setWrDt(date((Timestamp) asi.get_Value("WrDt")));
					item.setBchDtls(bch);
				}
			}

			totals.ass = totals.ass.add(ass);      // was: add(base)
			totals.cgst = totals.cgst.add(item.getCgstAmt());
			totals.sgst = totals.sgst.add(item.getSgstAmt());
			totals.igst = totals.igst.add(item.getIgstAmt());
			totals.ces = totals.ces.add(item.getCesAmt());

			items.add(item);
		}
		
		for (Map.Entry<Integer, BigDecimal> e : credit.entrySet())
			if (e.getValue().signum() > 0)
				throw new AdempiereException("Negative line(s) of " + e.getValue().toPlainString()
						+ " could not be deducted: no positive line with the same tax (or they exceed those lines). "
						+ "Use the same tax on the discount line as on the lines it discounts.");
		return items;
	}

	/**
	 * Fills GST/cess rate and amounts on the item.
	 * Child taxes are classified by name (CGST, SGST/UTGST, IGST, CESS); a single
	 * unclassified tax is treated as the GST rate and split by place of supply.
	 */
	private void applyTax(ItemList item, MInvoiceLine line, BigDecimal base, boolean igstApplies) {
		if (line.getC_Tax_ID() <= 0)
			throw new AdempiereException("Tax missing on invoice line " + line.getLine());
		MTax tax = new MTax(line.getCtx(), line.getC_Tax_ID(), line.get_TrxName());

		List<MTax> leaves = tax.isSummary() ? Arrays.asList(tax.getChildTaxes(false)) : Arrays.asList(tax);

		BigDecimal cgstRt = ZERO, sgstRt = ZERO, igstRt = ZERO, cesRt = ZERO;
		BigDecimal cgstAmt = ZERO, sgstAmt = ZERO, igstAmt = ZERO, cesAmt = ZERO;

		for (MTax t : leaves) {
			BigDecimal rate = t.getRate();
			BigDecimal amt = scale2(base.multiply(rate).divide(HUNDRED));
			String n = t.getName() == null ? "" : t.getName().toUpperCase();

			if (n.contains("CESS")) {
				cesRt = cesRt.add(rate);
				cesAmt = cesAmt.add(amt);
			} else if (n.contains("IGST")) {
				igstRt = igstRt.add(rate);
				igstAmt = igstAmt.add(amt);
			} else if (n.contains("CGST")) {
				cgstRt = cgstRt.add(rate);
				cgstAmt = cgstAmt.add(amt);
			} else if (n.contains("SGST")) { // also matches UTGST
				sgstRt = sgstRt.add(rate);
				sgstAmt = sgstAmt.add(amt);
			} else if (igstApplies) {
				igstRt = igstRt.add(rate);
				igstAmt = igstAmt.add(amt);
			} else {
				BigDecimal half = rate.divide(new BigDecimal("2"));
				BigDecimal halfAmt = scale2(base.multiply(half).divide(HUNDRED));
				cgstRt = cgstRt.add(half);
				sgstRt = sgstRt.add(half);
				cgstAmt = cgstAmt.add(halfAmt);
				sgstAmt = sgstAmt.add(halfAmt);
			}
		}
		
		// The tax setup must agree with the supply type. The IRP only reports a vague
		// "tax rate / value mismatch" here, so fail early and say which line and tax to fix.
		boolean hasCgstSgst = cgstRt.signum() > 0 || sgstRt.signum() > 0;
		boolean hasIgst = igstRt.signum() > 0;
		String taxName = tax.getName();

		if (igstApplies && hasCgstSgst)
		    throw new AdempiereException("Line " + line.getLine() + ": tax '" + taxName + "' splits into CGST/SGST, "
		            + "but this invoice must carry IGST (inter-state supply, export, SEZ, or IGST on intra-state). "
		            + "Select an IGST tax on the line.");

		if (!igstApplies && hasIgst)
		    throw new AdempiereException("Line " + line.getLine() + ": tax '" + taxName + "' is IGST, "
		            + "but this is an intra-state supply (seller and place of supply are in the same state). "
		            + "Select a CGST+SGST tax, or tick IGST on Intra-State if IGST is intended.");

		item.setGstRt(igstRt.add(cgstRt).add(sgstRt).setScale(3, RoundingMode.HALF_UP));
		item.setIgstAmt(igstAmt);
		item.setCgstAmt(cgstAmt);
		item.setSgstAmt(sgstAmt);
		item.setCesRt(cesRt.setScale(3, RoundingMode.HALF_UP));
		item.setCesAmt(cesAmt);
		item.setCesNonAdvlAmt(ZERO);
	}

	// =====================================================================
	// PAYMENT / REFERENCES / EXPORT
	// =====================================================================

	private PayDtls buildPayDtls(MSBInvoice invoice, BigDecimal grand, BigDecimal rate) {
		PayDtls p = new PayDtls();

		p.setMode(paymentMode(invoice.getPaymentRule()));
		p.setPayInstr(trunc(blank(invoice.getPayInstr()), 100));
		p.setCrTrn(trunc(blank(invoice.getCrTrn()), 100));
		p.setDirDr(trunc(blank(invoice.getDirDr()), 100));

		if (invoice.getC_PaymentTerm_ID() > 0) {
			MPaymentTerm pt = new MPaymentTerm(invoice.getCtx(), invoice.getC_PaymentTerm_ID(), invoice.get_TrxName());
			p.setPayTerm(trunc(pt.getName(), 100));
			p.setCrDay(pt.getNetDays());
		}

		BigDecimal open = scale2(invoice.getOpenAmt().multiply(rate)).abs();
		p.setPaymtDue(open);
		p.setPaidAmt(grand.abs().subtract(open).max(ZERO));
		return p;
	}

	private RefDtls buildRefDtls(MSBInvoice invoice) {
		Properties ctx = invoice.getCtx();
		String trx = invoice.get_TrxName();

		RefDtls r = new RefDtls();
		r.setInvRm(opt(invoice.getDescription(), 3, 100));

		// Invoice period
		// Invoice period: start falls back to the invoice date when only the end is filled
		if (invoice.getInvStDt() != null || invoice.getInvEndDt() != null) {
			if (invoice.getInvEndDt() == null)
				throw new AdempiereException("Invoice period end date is missing (the IRP needs both start and end)");
			DocPerdDtls per = new DocPerdDtls();
			per.setInvStDt(date(invoice.getInvStDt() != null ? invoice.getInvStDt() : invoice.getDateInvoiced()));
			per.setInvEndDt(date(invoice.getInvEndDt()));
			r.setDocPerdDtls(per);
		}

		// Preceding document (credit / debit notes)
		// Preceding document (credit / debit notes): the custom InvNo column wins,
		// otherwise the invoice's own Ref_Invoice_ID (Referenced Invoice) is used.
		String origNo = blank(invoice.getInvNo());
		MInvoice orig = null;
		if (origNo != null) {
			orig = new Query(ctx, MInvoice.Table_Name,
					"DocumentNo=? AND IsSOTrx='Y' AND DocStatus IN ('CO','CL') AND AD_Org_ID=? AND C_BPartner_ID=? AND C_Invoice_ID<>?",
					trx).setClient_ID()
							.setParameters(origNo, invoice.getAD_Org_ID(), invoice.getC_BPartner_ID(), invoice.getC_Invoice_ID())
							.setOrderBy("DateInvoiced DESC").first();
			if (orig == null)
				throw new AdempiereException("Original invoice not found: " + origNo);
		} else {
			int refId = invoice.get_ValueAsInt("Ref_Invoice_ID");
			if (refId > 0) {
				orig = new MInvoice(ctx, refId, trx);
				if (orig.get_ID() == 0 || !orig.isSOTrx())
					throw new AdempiereException("The referenced invoice is not a sales invoice");
				origNo = blank(orig.getDocumentNo());
			}
		}

		if (orig != null && origNo != null) {
			PrecDocDtls prec = new PrecDocDtls();
			prec.setInvNo(trunc(origNo, 16));
			prec.setInvDt(date(orig.getDateInvoiced()));
			prec.setOthRefNo(opt(invoice.getOthRefNo(), 1, 20));
			List<PrecDocDtls> precList = new ArrayList<>();
			precList.add(prec);
			r.setPrecDocDtls(precList);
		}

		// Contract / PO references
		String poRef = blank(invoice.getPOReference());
		String contr = blank(invoice.getContrRefr());
		String tend = blank(invoice.getTendRefr());
		String ext = blank(invoice.getExtRefr());
		if (poRef != null || contr != null || tend != null || ext != null) {
			ContrDtls c = new ContrDtls();
			c.setPoRefr(trunc(poRef, 16));
			c.setContrRefr(trunc(contr, 20));
			c.setTendRefr(trunc(tend, 20));
			c.setExtRefr(trunc(ext, 20));
			if (poRef != null && invoice.getC_Order_ID() > 0) {
				MOrder o = new MOrder(ctx, invoice.getC_Order_ID(), trx);
				c.setPoRefDt(date(o.getDateOrdered()));
			}
			List<ContrDtls> list = new ArrayList<>();
			list.add(c);
			r.setContrDtls(list);
		}

		return (r.getInvRm() == null && r.getDocPerdDtls() == null && r.getPrecDocDtls() == null
				&& r.getContrDtls() == null) ? null : r;
	}

	private ExpDtls buildExpDtls(MSBInvoice invoice, BigDecimal rate) {
		Properties ctx = invoice.getCtx();
		String trx = invoice.get_TrxName();

		MBPartnerLocation bpl = new MBPartnerLocation(ctx, invoice.getC_BPartner_Location_ID(), trx);
		MLocation loc = new MLocation(ctx, bpl.getC_Location_ID(), trx);

		ExpDtls e = new ExpDtls();
		e.setRefClm(yn(invoice.isRefClm()));
		e.setForCur(MCurrency.getISO_Code(ctx, invoice.getC_Currency_ID()));
		e.setCntCode(MCountry.get(ctx, loc.getC_Country_ID()).getCountryCode());
		e.setExpDuty(scale2(invoice.getExpDuty().multiply(rate)));
		e.setPort(opt(shipPort(invoice), 2, 10));
		e.setShipBNo(trunc(blank(invoice.getShipBNo()), 20));
		e.setShipBDt(date(invoice.getShipBDt()));
		return e;
	}

	// =====================================================================
	// SHIPMENT / E-WAY BILL
	// =====================================================================

	/**
	 * E-way bill part A from the custom M_InOut columns TransMode, Distance, VehNo
	 * and VehType. Returns null when there is no shipment or none of those are filled.
	 * A blank Distance is sent as 0, which asks the IRP to work it out from the PIN codes.
	 * TransId / TransName / TransDocNo / TransDocDt have no source column yet.
	 */
	private EwbDtls buildEwbDtls(MSBInvoice invoice) {
		MInOut shipment = findShipment(invoice);
		if (shipment == null)
			return null;

		String mode = blank(shipment.get_ValueAsString("TransMode"));
		String veh = blank(shipment.get_ValueAsString("VehNo"));
		String vehType = blank(shipment.get_ValueAsString("VehType"));
		Object dist = shipment.get_Value("Distance");
		if (mode == null && veh == null && vehType == null && dist == null)
			return null;

		EwbDtls e = new EwbDtls();
		e.setTransMode(mode);
		e.setVehNo(veh == null ? null : veh.replaceAll("[^A-Za-z0-9]", "").toUpperCase());
		e.setVehType(vehType);
		e.setDistance(dist == null ? BigDecimal.ZERO : new BigDecimal(dist.toString()));
		return e;
	}

	/** Shipment the invoice was raised from (via the invoice lines), else the order's latest completed shipment. */
	private MInOut findShipment(MSBInvoice invoice) {
		Properties ctx = invoice.getCtx();
		String trx = invoice.get_TrxName();

		for (MInvoiceLine line : invoice.getLines()) {
			if (line.getM_InOutLine_ID() > 0) {
				MInOutLine iol = new MInOutLine(ctx, line.getM_InOutLine_ID(), trx);
				if (iol.getM_InOut_ID() > 0)
					return new MInOut(ctx, iol.getM_InOut_ID(), trx);
			}
		}

		if (invoice.getC_Order_ID() > 0)
			return new Query(ctx, MInOut.Table_Name,
					"C_Order_ID=? AND IsSOTrx='Y' AND DocStatus IN ('CO','CL')", trx)
							.setClient_ID().setParameters(invoice.getC_Order_ID())
							.setOrderBy("MovementDate DESC, M_InOut_ID DESC").first();
		return null;
	}

	// =====================================================================
	// HELPERS
	// =====================================================================

	/** Free quantity from the custom C_InvoiceLine.FreeQty column (0 when blank or negative). */
	private static BigDecimal freeQty(MInvoiceLine line) {
		Object v = line.get_Value("FreeQty");
		BigDecimal q = v == null ? BigDecimal.ZERO : new BigDecimal(v.toString());
		return q.max(BigDecimal.ZERO).setScale(3, RoundingMode.HALF_UP);
	}

	/** Two-letter country of origin from the custom M_Product.C_Country_ID column. */
	private static String originCountry(Properties ctx, MProduct product) {
		int id = product.get_ValueAsInt("C_Country_ID");
		if (id <= 0)
			return null;
		MCountry c = MCountry.get(ctx, id);
		String code = c == null ? null : blank(c.getCountryCode());
		return code != null && code.length() == 2 ? code.toUpperCase() : null;
	}

	/** Port code from the custom ShipPort column, if that column exists on the invoice table. */
	private static String shipPort(PO po) {
		if (po.get_ColumnIndex("ShipPort") < 0)
			return null;
		return blank(po.get_ValueAsString("ShipPort"));
	}

	private static String yn(boolean v) {
		return v ? "Y" : "N";
	}

	private static String blank(String s) {
	    if (s == null)
	        return null;
	    s = clean(s).trim();
	    return s.isEmpty() ? null : s;
	}

	private static String trunc(String s, int max) {
	    if (s == null)
	        return null;
	    s = clean(s);
	    return s.length() <= max ? s : s.substring(0, max);
	}
	
	/** The IRP rejects " and \ in free text. Replace them one-for-one, so lengths (and the schema minimums) are unchanged. */
	private static String clean(String s) {
	    return s == null ? null : s.replace('"', '\'').replace('\\', '/');
	}
	
	/** Optional text field: trimmed and cut to max; null when blank or shorter than the schema minimum. */
	private static String opt(String s, int min, int max) {
	    String v = blank(s);
	    if (v == null)
	        return null;
	    v = trunc(v, max).trim();
	    return v.length() < min ? null : v;
	}

	/** Optional email: dropped unless it fits the IRP pattern and length (6-100). */
	private static String email(String s) {
	    String v = opt(s, 6, 100);
	    return v != null && v.matches(IrpPatterns.EMAIL) ? v : null;
	}

	private static BigDecimal scale2(BigDecimal v) {
		return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
	}

	private static String date(Timestamp ts) {
		if (ts == null)
			return null;
		return ts.toLocalDateTime().toLocalDate().format(IRP_DATE);
	}

	private static String phone(String raw) {
		if (raw == null)
			return null;
		String d = raw.replaceAll("\\D", "");
		return (d.length() >= 6 && d.length() <= 12) ? d : null;
	}

	private static String addr1(MLocation loc, String who) {
		String a = blank(loc.getAddress1());
		if (a == null)
			throw new AdempiereException(who + " address line 1 is missing");
		return trunc(a, 100);
	}
	
	/** Address line 2 is optional, but the schema's minimum is 3 characters, so a short value is dropped. */
	private static String addr2(MLocation loc) {
		String a = blank(loc.getAddress2());
		return (a == null || a.length() < 3) ? null : trunc(a, 100);
	}

	private static String city(MLocation loc, String who) {
		String c = null;
		if (loc.getC_City_ID() > 0)
			c = blank(new MCity(loc.getCtx(), loc.getC_City_ID(), loc.get_TrxName()).getName());
		if (c == null)
			c = blank(loc.getCity());
		if (c == null || c.length() < 3)
			throw new AdempiereException(who + " city is missing or shorter than 3 characters");
		return trunc(c, 50);
	}

	private static Integer pin(MLocation loc, String who) {
		String p = loc.getPostal() == null ? "" : loc.getPostal().replaceAll("\\D", "");
		if (p.length() != 6)
			throw new AdempiereException(who + " PIN code must be 6 digits, got: " + loc.getPostal());
		return Integer.valueOf(p);
	}

	/** State code from the GSTIN prefix, else from the location's region name. */
	/** Seller: state code from the first two digits of the GSTIN, else from the location's region. */
	private static String sellerStateCode(String gstin, MLocation loc) {
		if (gstin != null && gstin.length() >= 2 && gstin.substring(0, 2).matches("\\d{2}"))
			return gstin.substring(0, 2);
		return regionStateCode(loc, "Seller");
	}

	/**
	 * Buyer state code: the first two GSTIN digits (the IRP requires them to match Stcd).
	 * If the address region disagrees, fail early: the IRP also checks the PIN code against
	 * the state, so the invoice would be rejected anyway.
	 */
	private static String buyerStateCode(String gstin, MLocation loc, String name) {
	    String fromRegion = stateFromRegion(loc);
	    if (gstin.length() >= 2 && gstin.substring(0, 2).matches("\\d{2}")) {
	        String fromGstin = gstin.substring(0, 2);
	        if (fromRegion != null && !fromRegion.equals(fromGstin))
	            throw new AdempiereException("Buyer " + name + ": GSTIN " + gstin + " belongs to state "
	                    + fromGstin + " but the billing address region is state " + fromRegion
	                    + ". Use the address registered for this GSTIN, or correct the Tax ID.");
	        return fromGstin;
	    }
	    return regionStateCode(loc, "Buyer");
	}

	private static String regionStateCode(MLocation loc, String who) {
		String code = stateFromRegion(loc);
		if (code == null)
			throw new AdempiereException(who + " state code could not be determined: enter a GSTIN or a "
					+ "recognised region (state) on the address");
		return code;
	}

	private static String stateFromRegion(MLocation loc) {
	    if (loc.getC_Region_ID() <= 0)
	        return null;
	    MRegion region = MRegion.get(loc.getCtx(), loc.getC_Region_ID());
	    return region == null ? null : blank(region.get_ValueAsString("GstStateCode"));
	}

	private static String uomCode(MInvoiceLine line) {
		MUOM uom = MUOM.get(line.getC_UOM_ID());
		String x12 = uom == null ? null : blank(uom.getX12DE355());
		if (x12 == null)
			return "OTH";
		String mapped = UOM_CODES.get(x12.toUpperCase());
		return mapped != null ? mapped : "OTH";
	}
	
	private static int inrCurrencyId() {
		int id = DB.getSQLValue(null, "SELECT C_Currency_ID FROM C_Currency WHERE ISO_Code='INR' AND IsActive='Y'");
		if (id <= 0)
			throw new AdempiereException("Currency INR is not defined or not active");
		return id;
	}

	/** Rate from the invoice currency to INR, using the invoice's conversion type and accounting date. */
	private static BigDecimal inrRate(MSBInvoice invoice, int inrId) {
		BigDecimal rate = MConversionRate.getRate(invoice.getC_Currency_ID(), inrId, invoice.getDateAcct(),
				invoice.getC_ConversionType_ID(), invoice.getAD_Client_ID(), invoice.getAD_Org_ID());
		if (rate == null || rate.signum() <= 0)
			throw new AdempiereException("No currency rate to INR for "
					+ MCurrency.getISO_Code(invoice.getCtx(), invoice.getC_Currency_ID())
					+ " on " + date(invoice.getDateAcct()) + ". Define a rate in the Currency Rate window.");
		return rate;
	}
	
	/** Sum of the leaf tax rates for the line (CGST + SGST + IGST + CESS, or the single GST rate). */
	private static BigDecimal totalTaxRate(MInvoiceLine line) {
		if (line.getC_Tax_ID() <= 0)
			throw new AdempiereException("Tax missing on invoice line " + line.getLine());
		MTax tax = new MTax(line.getCtx(), line.getC_Tax_ID(), line.get_TrxName());
		List<MTax> leaves = tax.isSummary() ? Arrays.asList(tax.getChildTaxes(false)) : Arrays.asList(tax);
		BigDecimal sum = BigDecimal.ZERO;
		for (MTax t : leaves)
			sum = sum.add(t.getRate());
		return sum;
	}
	
	/** Taxable value of a line in INR (tax-included prices are grossed down). */
	private BigDecimal taxableValue(MSBInvoice invoice, MInvoiceLine line, BigDecimal rate) {
		BigDecimal gross = line.getLineNetAmt().multiply(rate);
		if (invoice.isTaxIncluded()) {
			BigDecimal divisor = HUNDRED.add(totalTaxRate(line));
			return scale2(gross.multiply(HUNDRED).divide(divisor, 10, RoundingMode.HALF_UP));
		}
		return scale2(gross);
	}

	private static String paymentMode(String rule) {
		if (rule == null)
			return null;
		switch (rule) {
		case "B":
			return "CASH";
		case "K":
			return "CARD";
		case "S":
			return "CHEQUE";
		case "D":
			return "DIRECT DEBIT";
		case "T":
			return "CREDIT TRANSFER";
		case "P":
			return "CREDIT";
		default:
			return null;
		}
	}
}