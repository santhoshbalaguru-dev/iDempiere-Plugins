package com.santhoshbalaguru.einvoice.model.generateirn.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DocPerdDtls {

    @JsonProperty("InvStDt")
    @IrpField(min = 10, max = 10, pattern = IrpPatterns.DATE, required = true)
    private String invStDt;

    @JsonProperty("InvEndDt")
    @IrpField(min = 10, max = 10, pattern = IrpPatterns.DATE, required = true)
    private String invEndDt;

    public String getInvStDt() {
        return invStDt;
    }

    public void setInvStDt(String invStDt) {
        this.invStDt = invStDt;
    }

    public String getInvEndDt() {
        return invEndDt;
    }

    public void setInvEndDt(String invEndDt) {
        this.invEndDt = invEndDt;
    }
}