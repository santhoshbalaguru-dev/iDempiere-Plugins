package com.santhoshbalaguru.einvoice.model.generateirn.request;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PayDtls {

    @JsonProperty("Nm")
    @IrpField(min = 1, max = 100, pattern = IrpPatterns.NO_QUOTE)
    private String nm;

    @JsonProperty("AccDet")
    @IrpField(min = 1, max = 18, pattern = IrpPatterns.NO_QUOTE)
    private String accDet;

    @JsonProperty("Mode")
    @IrpField(min = 1, max = 18, pattern = IrpPatterns.NO_QUOTE)
    private String mode;

    @JsonProperty("FinInsBr")
    @IrpField(min = 1, max = 11, pattern = IrpPatterns.NO_QUOTE)
    private String finInsBr;

    @JsonProperty("PayTerm")
    @IrpField(min = 1, max = 100, pattern = IrpPatterns.NO_QUOTE)
    private String payTerm;

    @JsonProperty("PayInstr")
    @IrpField(min = 1, max = 100, pattern = IrpPatterns.NO_QUOTE)
    private String payInstr;

    @JsonProperty("CrTrn")
    @IrpField(min = 1, max = 100, pattern = IrpPatterns.NO_QUOTE)
    private String crTrn;

    @JsonProperty("DirDr")
    @IrpField(min = 1, max = 100, pattern = IrpPatterns.NO_QUOTE)
    private String dirDr;

    @JsonProperty("CrDay")
    @IrpField(minVal = "0", maxVal = "9999")
    private Integer crDay;

    @JsonProperty("PaidAmt")
    @IrpField(minVal = "0", maxVal = IrpPatterns.TOT_MAX)
    private BigDecimal paidAmt;

    @JsonProperty("PaymtDue")
    @IrpField(minVal = "0", maxVal = IrpPatterns.TOT_MAX)
    private BigDecimal paymtDue;

    public String getNm() {
        return nm;
    }

    public void setNm(String nm) {
        this.nm = nm;
    }

    public String getAccDet() {
        return accDet;
    }

    public void setAccDet(String accDet) {
        this.accDet = accDet;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getFinInsBr() {
        return finInsBr;
    }

    public void setFinInsBr(String finInsBr) {
        this.finInsBr = finInsBr;
    }

    public String getPayTerm() {
        return payTerm;
    }

    public void setPayTerm(String payTerm) {
        this.payTerm = payTerm;
    }

    public String getPayInstr() {
        return payInstr;
    }

    public void setPayInstr(String payInstr) {
        this.payInstr = payInstr;
    }

    public String getCrTrn() {
        return crTrn;
    }

    public void setCrTrn(String crTrn) {
        this.crTrn = crTrn;
    }

    public String getDirDr() {
        return dirDr;
    }

    public void setDirDr(String dirDr) {
        this.dirDr = dirDr;
    }

    public Integer getCrDay() {
        return crDay;
    }

    public void setCrDay(Integer crDay) {
        this.crDay = crDay;
    }

    public BigDecimal getPaidAmt() {
        return paidAmt;
    }

    public void setPaidAmt(BigDecimal paidAmt) {
        this.paidAmt = paidAmt;
    }

    public BigDecimal getPaymtDue() {
        return paymtDue;
    }

    public void setPaymtDue(BigDecimal paymtDue) {
        this.paymtDue = paymtDue;
    }
}