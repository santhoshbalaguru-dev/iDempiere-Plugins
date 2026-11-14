package com.santhoshbalaguru.einvoice.model.generateirn.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BchDtls {

    @JsonProperty("Nm")
    @IrpField(min = 3, max = 20, pattern = IrpPatterns.NO_QUOTE, required = true)
    private String nm;

    @JsonProperty("ExpDt")
    @IrpField(min = 10, max = 10, pattern = IrpPatterns.DATE)
    private String expDt;

    @JsonProperty("WrDt")
    @IrpField(min = 10, max = 10, pattern = IrpPatterns.DATE)
    private String wrDt;

    public String getNm() {
        return nm;
    }

    public void setNm(String nm) {
        this.nm = nm;
    }

    public String getExpDt() {
        return expDt;
    }

    public void setExpDt(String expDt) {
        this.expDt = expDt;
    }

    public String getWrDt() {
        return wrDt;
    }

    public void setWrDt(String wrDt) {
        this.wrDt = wrDt;
    }
}