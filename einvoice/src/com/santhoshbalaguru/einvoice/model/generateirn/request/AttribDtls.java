package com.santhoshbalaguru.einvoice.model.generateirn.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AttribDtls {

    @JsonProperty("Nm")
    @IrpField(min = 1, max = 100, pattern = IrpPatterns.NO_QUOTE)
    private String nm;

    @JsonProperty("Val")
    @IrpField(min = 1, max = 100, pattern = IrpPatterns.NO_QUOTE)
    private String val;

    public String getNm() {
        return nm;
    }

    public void setNm(String nm) {
        this.nm = nm;
    }

    public String getVal() {
        return val;
    }

    public void setVal(String val) {
        this.val = val;
    }
}