package com.santhoshbalaguru.einvoice.model.generateirn.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BuyerDtls {

    @JsonProperty("Gstin")
    @IrpField(min = 3, max = 15, pattern = IrpPatterns.GSTIN_OR_URP, required = true)
    private String gstin;

    @JsonProperty("LglNm")
    @IrpField(min = 3, max = 100, pattern = IrpPatterns.NO_QUOTE, required = true)
    private String lglNm;

    @JsonProperty("TrdNm")
    @IrpField(min = 3, max = 100, pattern = IrpPatterns.NO_QUOTE)
    private String trdNm;

    @JsonProperty("Pos")
    @IrpField(min = 1, max = 2, pattern = IrpPatterns.STATE_CODE, required = true)
    private String pos;

    @JsonProperty("Addr1")
    @IrpField(min = 1, max = 100, pattern = IrpPatterns.NO_QUOTE, required = true)
    private String addr1;

    @JsonProperty("Addr2")
    @IrpField(min = 3, max = 100, pattern = IrpPatterns.NO_QUOTE)
    private String addr2;

    @JsonProperty("Loc")
    @IrpField(min = 3, max = 100, pattern = IrpPatterns.NO_QUOTE, required = true)
    private String loc;

    @JsonProperty("Pin")
    @IrpField(minVal = IrpPatterns.PIN_MIN, maxVal = IrpPatterns.PIN_MAX)
    private Integer pin;

    @JsonProperty("Stcd")
    @IrpField(min = 1, max = 2, pattern = IrpPatterns.STATE_CODE, required = true)
    private String stcd;

    @JsonProperty("Ph")
    @IrpField(min = 6, max = 12, pattern = IrpPatterns.PHONE)
    private String ph;

    @JsonProperty("Em")
    @IrpField(min = 6, max = 100, pattern = IrpPatterns.EMAIL)
    private String em;

    public String getGstin() {
        return gstin;
    }

    public void setGstin(String gstin) {
        this.gstin = gstin;
    }

    public String getLglNm() {
        return lglNm;
    }

    public void setLglNm(String lglNm) {
        this.lglNm = lglNm;
    }

    public String getTrdNm() {
        return trdNm;
    }

    public void setTrdNm(String trdNm) {
        this.trdNm = trdNm;
    }

    public String getPos() {
        return pos;
    }

    public void setPos(String pos) {
        this.pos = pos;
    }

    public String getAddr1() {
        return addr1;
    }

    public void setAddr1(String addr1) {
        this.addr1 = addr1;
    }

    public String getAddr2() {
        return addr2;
    }

    public void setAddr2(String addr2) {
        this.addr2 = addr2;
    }

    public String getLoc() {
        return loc;
    }

    public void setLoc(String loc) {
        this.loc = loc;
    }

    public Integer getPin() {
        return pin;
    }

    public void setPin(Integer pin) {
        this.pin = pin;
    }

    public String getStcd() {
        return stcd;
    }

    public void setStcd(String stcd) {
        this.stcd = stcd;
    }

    public String getPh() {
        return ph;
    }

    public void setPh(String ph) {
        this.ph = ph;
    }

    public String getEm() {
        return em;
    }

    public void setEm(String em) {
        this.em = em;
    }
}