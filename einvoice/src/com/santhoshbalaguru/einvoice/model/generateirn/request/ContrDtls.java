package com.santhoshbalaguru.einvoice.model.generateirn.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ContrDtls {

    @JsonProperty("RecAdvRefr")
    @IrpField(min = 1, max = 20, pattern = IrpPatterns.NO_QUOTE)
    private String recAdvRefr;

    @JsonProperty("RecAdvDt")
    @IrpField(min = 10, max = 10, pattern = IrpPatterns.DATE)
    private String recAdvDt;

    @JsonProperty("TendRefr")
    @IrpField(min = 1, max = 20, pattern = IrpPatterns.NO_QUOTE)
    private String tendRefr;

    @JsonProperty("ContrRefr")
    @IrpField(min = 1, max = 20, pattern = IrpPatterns.NO_QUOTE)
    private String contrRefr;

    @JsonProperty("ExtRefr")
    @IrpField(min = 1, max = 20, pattern = IrpPatterns.NO_QUOTE)
    private String extRefr;

    @JsonProperty("ProjRefr")
    @IrpField(min = 1, max = 20, pattern = IrpPatterns.NO_QUOTE)
    private String projRefr;

    @JsonProperty("PORefr")
    @IrpField(min = 1, max = 16, pattern = IrpPatterns.NO_QUOTE)
    private String poRefr;

    @JsonProperty("PORefDt")
    @IrpField(min = 10, max = 10, pattern = IrpPatterns.DATE)
    private String poRefDt;

    public String getRecAdvRefr() {
        return recAdvRefr;
    }

    public void setRecAdvRefr(String recAdvRefr) {
        this.recAdvRefr = recAdvRefr;
    }

    public String getRecAdvDt() {
        return recAdvDt;
    }

    public void setRecAdvDt(String recAdvDt) {
        this.recAdvDt = recAdvDt;
    }

    public String getTendRefr() {
        return tendRefr;
    }

    public void setTendRefr(String tendRefr) {
        this.tendRefr = tendRefr;
    }

    public String getContrRefr() {
        return contrRefr;
    }

    public void setContrRefr(String contrRefr) {
        this.contrRefr = contrRefr;
    }

    public String getExtRefr() {
        return extRefr;
    }

    public void setExtRefr(String extRefr) {
        this.extRefr = extRefr;
    }

    public String getProjRefr() {
        return projRefr;
    }

    public void setProjRefr(String projRefr) {
        this.projRefr = projRefr;
    }

    public String getPoRefr() {
        return poRefr;
    }

    public void setPoRefr(String poRefr) {
        this.poRefr = poRefr;
    }

    public String getPoRefDt() {
        return poRefDt;
    }

    public void setPoRefDt(String poRefDt) {
        this.poRefDt = poRefDt;
    }
}