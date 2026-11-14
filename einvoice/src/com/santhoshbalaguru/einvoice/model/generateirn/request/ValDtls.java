package com.santhoshbalaguru.einvoice.model.generateirn.request;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ValDtls {

    @JsonProperty("AssVal")
    @IrpField(minVal = "0", maxVal = IrpPatterns.TOT_MAX, required = true)
    private BigDecimal assVal;

    @JsonProperty("CgstVal")
    @IrpField(minVal = "0", maxVal = IrpPatterns.TOT_MAX)
    private BigDecimal cgstVal;

    @JsonProperty("SgstVal")
    @IrpField(minVal = "0", maxVal = IrpPatterns.TOT_MAX)
    private BigDecimal sgstVal;

    @JsonProperty("IgstVal")
    @IrpField(minVal = "0", maxVal = IrpPatterns.TOT_MAX)
    private BigDecimal igstVal;

    @JsonProperty("CesVal")
    @IrpField(minVal = "0", maxVal = IrpPatterns.TOT_MAX)
    private BigDecimal cesVal;

    @JsonProperty("StCesVal")
    @IrpField(minVal = "0", maxVal = IrpPatterns.TOT_MAX)
    private BigDecimal stCesVal;

    @JsonProperty("Discount")
    @IrpField(minVal = "0", maxVal = IrpPatterns.TOT_MAX)
    private BigDecimal discount;

    @JsonProperty("OthChrg")
    @IrpField(minVal = "0", maxVal = IrpPatterns.TOT_MAX)
    private BigDecimal othChrg;

    @JsonProperty("RndOffAmt")
    @IrpField(minVal = "-99.99", maxVal = "99.99")
    private BigDecimal rndOffAmt;

    @JsonProperty("TotInvVal")
    @IrpField(minVal = "0", maxVal = IrpPatterns.TOT_MAX, required = true)
    private BigDecimal totInvVal;

    @JsonProperty("TotInvValFc")
    @IrpField(minVal = "0", maxVal = IrpPatterns.TOT_MAX)
    private BigDecimal totInvValFc;

    public BigDecimal getAssVal() {
        return assVal;
    }

    public void setAssVal(BigDecimal assVal) {
        this.assVal = assVal;
    }

    public BigDecimal getCgstVal() {
        return cgstVal;
    }

    public void setCgstVal(BigDecimal cgstVal) {
        this.cgstVal = cgstVal;
    }

    public BigDecimal getSgstVal() {
        return sgstVal;
    }

    public void setSgstVal(BigDecimal sgstVal) {
        this.sgstVal = sgstVal;
    }

    public BigDecimal getIgstVal() {
        return igstVal;
    }

    public void setIgstVal(BigDecimal igstVal) {
        this.igstVal = igstVal;
    }

    public BigDecimal getCesVal() {
        return cesVal;
    }

    public void setCesVal(BigDecimal cesVal) {
        this.cesVal = cesVal;
    }

    public BigDecimal getStCesVal() {
        return stCesVal;
    }

    public void setStCesVal(BigDecimal stCesVal) {
        this.stCesVal = stCesVal;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public void setDiscount(BigDecimal discount) {
        this.discount = discount;
    }

    public BigDecimal getOthChrg() {
        return othChrg;
    }

    public void setOthChrg(BigDecimal othChrg) {
        this.othChrg = othChrg;
    }

    public BigDecimal getRndOffAmt() {
        return rndOffAmt;
    }

    public void setRndOffAmt(BigDecimal rndOffAmt) {
        this.rndOffAmt = rndOffAmt;
    }

    public BigDecimal getTotInvVal() {
        return totInvVal;
    }

    public void setTotInvVal(BigDecimal totInvVal) {
        this.totInvVal = totInvVal;
    }

    public BigDecimal getTotInvValFc() {
        return totInvValFc;
    }

    public void setTotInvValFc(BigDecimal totInvValFc) {
        this.totInvValFc = totInvValFc;
    }
}