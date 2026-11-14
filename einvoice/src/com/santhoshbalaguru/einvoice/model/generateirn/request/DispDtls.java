package com.santhoshbalaguru.einvoice.model.generateirn.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DispDtls {

    @JsonProperty("Nm")
    @IrpField(min = 3, max = 100, pattern = IrpPatterns.NO_QUOTE, required = true)
    private String nm;

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
    @IrpField(minVal = IrpPatterns.PIN_MIN, maxVal = IrpPatterns.PIN_MAX, required = true)
    private Integer pin;

    @JsonProperty("Stcd")
    @IrpField(min = 1, max = 2, pattern = IrpPatterns.STATE_CODE, required = true)
    private String stcd;

    public String getNm() {
        return nm;
    }

    public void setNm(String nm) {
        this.nm = nm;
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
}