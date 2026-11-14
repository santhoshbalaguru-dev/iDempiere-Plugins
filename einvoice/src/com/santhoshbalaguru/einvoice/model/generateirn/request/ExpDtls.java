package com.santhoshbalaguru.einvoice.model.generateirn.request;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ExpDtls {

    @JsonProperty("ShipBNo")
    @IrpField(min = 1, max = 20, pattern = IrpPatterns.NO_QUOTE)
    private String shipBNo;

    @JsonProperty("ShipBDt")
    @IrpField(min = 10, max = 10, pattern = IrpPatterns.DATE)
    private String shipBDt;

    @JsonProperty("Port")
    @IrpField(min = 2, max = 10, pattern = IrpPatterns.PORT)
    private String port;

    @JsonProperty("RefClm")
    @IrpField(min = 1, max = 1, pattern = IrpPatterns.YN)
    private String refClm;

    @JsonProperty("ForCur")
    @IrpField(min = 3, max = 16, pattern = IrpPatterns.FOR_CUR)
    private String forCur;

    @JsonProperty("CntCode")
    @IrpField(min = 2, max = 2, pattern = IrpPatterns.CNT_CODE)
    private String cntCode;

    @JsonProperty("ExpDuty")
    @IrpField(minVal = "0", maxVal = IrpPatterns.AMT_MAX)
    private BigDecimal expDuty;

    public String getShipBNo() {
        return shipBNo;
    }

    public void setShipBNo(String shipBNo) {
        this.shipBNo = shipBNo;
    }

    public String getShipBDt() {
        return shipBDt;
    }

    public void setShipBDt(String shipBDt) {
        this.shipBDt = shipBDt;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getRefClm() {
        return refClm;
    }

    public void setRefClm(String refClm) {
        this.refClm = refClm;
    }

    public String getForCur() {
        return forCur;
    }

    public void setForCur(String forCur) {
        this.forCur = forCur;
    }

    public String getCntCode() {
        return cntCode;
    }

    public void setCntCode(String cntCode) {
        this.cntCode = cntCode;
    }

    public BigDecimal getExpDuty() {
        return expDuty;
    }

    public void setExpDuty(BigDecimal expDuty) {
        this.expDuty = expDuty;
    }
}