package com.santhoshbalaguru.einvoice.model.generateirn.request;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ItemList {

    @JsonProperty("SlNo")
    @IrpField(min = 1, max = 6, pattern = IrpPatterns.SL_NO, required = true)
    private String slNo;

    @JsonProperty("PrdDesc")
    @IrpField(min = 3, max = 300, pattern = IrpPatterns.NO_QUOTE)
    private String prdDesc;

    @JsonProperty("IsServc")
    @IrpField(min = 1, max = 1, allowed = {"Y", "N"}, required = true)
    private String isServc;

    @JsonProperty("HsnCd")
    @IrpField(min = 4, max = 8, pattern = IrpPatterns.HSN, required = true)
    private String hsnCd;

    @JsonProperty("Barcde")
    @IrpField(min = 3, max = 30, pattern = IrpPatterns.NO_QUOTE)
    private String barcde;

    @JsonProperty("Qty")
    @IrpField(minVal = "0", maxVal = "9999999999.999")
    private BigDecimal qty;

    @JsonProperty("FreeQty")
    @IrpField(minVal = "0", maxVal = "9999999999.999")
    private BigDecimal freeQty;

    @JsonProperty("Unit")
    @IrpField(min = 3, max = 8, pattern = IrpPatterns.UNIT)
    private String unit;

    @JsonProperty("UnitPrice")
    @IrpField(minVal = "0", maxVal = "999999999999.999", required = true)
    private BigDecimal unitPrice;

    @JsonProperty("TotAmt")
    @IrpField(minVal = "0", maxVal = IrpPatterns.AMT_MAX, required = true)
    private BigDecimal totAmt;

    @JsonProperty("Discount")
    @IrpField(minVal = "0", maxVal = IrpPatterns.AMT_MAX)
    private BigDecimal discount;

    @JsonProperty("PreTaxVal")
    @IrpField(minVal = "0", maxVal = IrpPatterns.AMT_MAX)
    private BigDecimal preTaxVal;

    @JsonProperty("AssAmt")
    @IrpField(minVal = "0", maxVal = IrpPatterns.AMT_MAX, required = true)
    private BigDecimal assAmt;

    @JsonProperty("GstRt")
    @IrpField(minVal = "0", maxVal = IrpPatterns.RATE_MAX, required = true)
    private BigDecimal gstRt;

    @JsonProperty("IgstAmt")
    @IrpField(minVal = "0", maxVal = IrpPatterns.AMT_MAX)
    private BigDecimal igstAmt;

    @JsonProperty("CgstAmt")
    @IrpField(minVal = "0", maxVal = IrpPatterns.AMT_MAX)
    private BigDecimal cgstAmt;

    @JsonProperty("SgstAmt")
    @IrpField(minVal = "0", maxVal = IrpPatterns.AMT_MAX)
    private BigDecimal sgstAmt;

    @JsonProperty("CesRt")
    @IrpField(minVal = "0", maxVal = IrpPatterns.RATE_MAX)
    private BigDecimal cesRt;

    @JsonProperty("CesAmt")
    @IrpField(minVal = "0", maxVal = IrpPatterns.AMT_MAX)
    private BigDecimal cesAmt;

    @JsonProperty("CesNonAdvlAmt")
    @IrpField(minVal = "0", maxVal = IrpPatterns.AMT_MAX)
    private BigDecimal cesNonAdvlAmt;

    @JsonProperty("StateCesRt")
    @IrpField(minVal = "0", maxVal = IrpPatterns.RATE_MAX)
    private BigDecimal stateCesRt;

    @JsonProperty("StateCesAmt")
    @IrpField(minVal = "0", maxVal = IrpPatterns.AMT_MAX)
    private BigDecimal stateCesAmt;

    @JsonProperty("StateCesNonAdvlAmt")
    @IrpField(minVal = "0", maxVal = IrpPatterns.AMT_MAX)
    private BigDecimal stateCesNonAdvlAmt;

    @JsonProperty("OthChrg")
    @IrpField(minVal = "0", maxVal = IrpPatterns.AMT_MAX)
    private BigDecimal othChrg;

    @JsonProperty("TotItemVal")
    @IrpField(minVal = "0", maxVal = IrpPatterns.AMT_MAX, required = true)
    private BigDecimal totItemVal;

    @JsonProperty("OrdLineRef")
    @IrpField(min = 1, max = 50, pattern = IrpPatterns.NO_QUOTE)
    private String ordLineRef;

    @JsonProperty("OrgCntry")
    @IrpField(min = 2, max = 2, pattern = IrpPatterns.COUNTRY_2)
    private String orgCntry;

    @JsonProperty("PrdSlNo")
    @IrpField(min = 1, max = 20, pattern = IrpPatterns.NO_QUOTE)
    private String prdSlNo;

    @JsonProperty("BchDtls")
    private BchDtls bchDtls;

    @JsonProperty("AttribDtls")
    private List<AttribDtls> attribDtls;

    public String getSlNo() {
        return slNo;
    }

    public void setSlNo(String slNo) {
        this.slNo = slNo;
    }

    public String getPrdDesc() {
        return prdDesc;
    }

    public void setPrdDesc(String prdDesc) {
        this.prdDesc = prdDesc;
    }

    public String getIsServc() {
        return isServc;
    }

    public void setIsServc(String isServc) {
        this.isServc = isServc;
    }

    public String getHsnCd() {
        return hsnCd;
    }

    public void setHsnCd(String hsnCd) {
        this.hsnCd = hsnCd;
    }

    public String getBarcde() {
        return barcde;
    }

    public void setBarcde(String barcde) {
        this.barcde = barcde;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public BigDecimal getFreeQty() {
        return freeQty;
    }

    public void setFreeQty(BigDecimal freeQty) {
        this.freeQty = freeQty;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getTotAmt() {
        return totAmt;
    }

    public void setTotAmt(BigDecimal totAmt) {
        this.totAmt = totAmt;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public void setDiscount(BigDecimal discount) {
        this.discount = discount;
    }

    public BigDecimal getPreTaxVal() {
        return preTaxVal;
    }

    public void setPreTaxVal(BigDecimal preTaxVal) {
        this.preTaxVal = preTaxVal;
    }

    public BigDecimal getAssAmt() {
        return assAmt;
    }

    public void setAssAmt(BigDecimal assAmt) {
        this.assAmt = assAmt;
    }

    public BigDecimal getGstRt() {
        return gstRt;
    }

    public void setGstRt(BigDecimal gstRt) {
        this.gstRt = gstRt;
    }

    public BigDecimal getIgstAmt() {
        return igstAmt;
    }

    public void setIgstAmt(BigDecimal igstAmt) {
        this.igstAmt = igstAmt;
    }

    public BigDecimal getCgstAmt() {
        return cgstAmt;
    }

    public void setCgstAmt(BigDecimal cgstAmt) {
        this.cgstAmt = cgstAmt;
    }

    public BigDecimal getSgstAmt() {
        return sgstAmt;
    }

    public void setSgstAmt(BigDecimal sgstAmt) {
        this.sgstAmt = sgstAmt;
    }

    public BigDecimal getCesRt() {
        return cesRt;
    }

    public void setCesRt(BigDecimal cesRt) {
        this.cesRt = cesRt;
    }

    public BigDecimal getCesAmt() {
        return cesAmt;
    }

    public void setCesAmt(BigDecimal cesAmt) {
        this.cesAmt = cesAmt;
    }

    public BigDecimal getCesNonAdvlAmt() {
        return cesNonAdvlAmt;
    }

    public void setCesNonAdvlAmt(BigDecimal cesNonAdvlAmt) {
        this.cesNonAdvlAmt = cesNonAdvlAmt;
    }

    public BigDecimal getStateCesRt() {
        return stateCesRt;
    }

    public void setStateCesRt(BigDecimal stateCesRt) {
        this.stateCesRt = stateCesRt;
    }

    public BigDecimal getStateCesAmt() {
        return stateCesAmt;
    }

    public void setStateCesAmt(BigDecimal stateCesAmt) {
        this.stateCesAmt = stateCesAmt;
    }

    public BigDecimal getStateCesNonAdvlAmt() {
        return stateCesNonAdvlAmt;
    }

    public void setStateCesNonAdvlAmt(BigDecimal stateCesNonAdvlAmt) {
        this.stateCesNonAdvlAmt = stateCesNonAdvlAmt;
    }

    public BigDecimal getOthChrg() {
        return othChrg;
    }

    public void setOthChrg(BigDecimal othChrg) {
        this.othChrg = othChrg;
    }

    public BigDecimal getTotItemVal() {
        return totItemVal;
    }

    public void setTotItemVal(BigDecimal totItemVal) {
        this.totItemVal = totItemVal;
    }

    public String getOrdLineRef() {
        return ordLineRef;
    }

    public void setOrdLineRef(String ordLineRef) {
        this.ordLineRef = ordLineRef;
    }

    public String getOrgCntry() {
        return orgCntry;
    }

    public void setOrgCntry(String orgCntry) {
        this.orgCntry = orgCntry;
    }

    public String getPrdSlNo() {
        return prdSlNo;
    }

    public void setPrdSlNo(String prdSlNo) {
        this.prdSlNo = prdSlNo;
    }

    public BchDtls getBchDtls() {
        return bchDtls;
    }

    public void setBchDtls(BchDtls bchDtls) {
        this.bchDtls = bchDtls;
    }

    public List<AttribDtls> getAttribDtls() {
        return attribDtls;
    }

    public void setAttribDtls(List<AttribDtls> attribDtls) {
        this.attribDtls = attribDtls;
    }
}