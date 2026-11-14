package com.santhoshbalaguru.einvoice.model.generateirn.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DocDtls {

    @JsonProperty("Typ")
    @IrpField(min = 3, max = 3, allowed = {"INV", "CRN", "DBN"}, required = true)
    private String typ;

    @JsonProperty("No")
    @IrpField(min = 1, max = 16, pattern = IrpPatterns.DOC_NO, required = true)
    private String no;

    @JsonProperty("Dt")
    @IrpField(min = 10, max = 10, pattern = IrpPatterns.DATE, required = true)
    private String dt;

    public String getTyp() {
        return typ;
    }

    public void setTyp(String typ) {
        this.typ = typ;
    }

    public String getNo() {
        return no;
    }

    public void setNo(String no) {
        this.no = no;
    }

    public String getDt() {
        return dt;
    }

    public void setDt(String dt) {
        this.dt = dt;
    }
}