package com.santhoshbalaguru.einvoice.model.generateirn.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TranDtls {

    @JsonProperty("TaxSch")
    @IrpField(min = 3, max = 10, allowed = {"GST"}, required = true)
    private String taxSch;

    @JsonProperty("SupTyp")
    @IrpField(min = 3, max = 10, allowed = {"B2B", "SEZWP", "SEZWOP", "EXPWP", "EXPWOP", "DEXP"}, required = true)
    private String supTyp;

    @JsonProperty("RegRev")
    @IrpField(min = 1, max = 1, allowed = {"Y", "N"})
    private String regRev;

    @JsonProperty("EcmGstin")
    @IrpField(min = 15, max = 15, pattern = IrpPatterns.GSTIN)
    private String ecmGstin;

    @JsonProperty("IgstOnIntra")
    @IrpField(min = 1, max = 1, allowed = {"Y", "N"})
    private String igstOnIntra;

    public String getTaxSch() {
        return taxSch;
    }

    public void setTaxSch(String taxSch) {
        this.taxSch = taxSch;
    }

    public String getSupTyp() {
        return supTyp;
    }

    public void setSupTyp(String supTyp) {
        this.supTyp = supTyp;
    }

    public String getRegRev() {
        return regRev;
    }

    public void setRegRev(String regRev) {
        this.regRev = regRev;
    }

    public String getEcmGstin() {
        return ecmGstin;
    }

    public void setEcmGstin(String ecmGstin) {
        this.ecmGstin = ecmGstin;
    }

    public String getIgstOnIntra() {
        return igstOnIntra;
    }

    public void setIgstOnIntra(String igstOnIntra) {
        this.igstOnIntra = igstOnIntra;
    }
}