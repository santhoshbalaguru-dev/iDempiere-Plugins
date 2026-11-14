package com.santhoshbalaguru.einvoice.model.generateirn.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PrecDocDtls {

    @JsonProperty("InvNo")
    @IrpField(min = 1, max = 16, pattern = IrpPatterns.PREC_INV_NO, required = true)
    private String invNo;

    @JsonProperty("InvDt")
    @IrpField(min = 10, max = 10, pattern = IrpPatterns.DATE, required = true)
    private String invDt;

    @JsonProperty("OthRefNo")
    @IrpField(min = 1, max = 20, pattern = IrpPatterns.NO_QUOTE)
    private String othRefNo;

    public String getInvNo() {
        return invNo;
    }

    public void setInvNo(String invNo) {
        this.invNo = invNo;
    }

    public String getInvDt() {
        return invDt;
    }

    public void setInvDt(String invDt) {
        this.invDt = invDt;
    }

    public String getOthRefNo() {
        return othRefNo;
    }

    public void setOthRefNo(String othRefNo) {
        this.othRefNo = othRefNo;
    }
}