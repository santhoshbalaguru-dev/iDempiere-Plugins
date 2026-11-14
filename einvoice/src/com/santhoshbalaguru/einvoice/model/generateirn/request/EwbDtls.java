package com.santhoshbalaguru.einvoice.model.generateirn.request;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EwbDtls {

    @JsonProperty("TransId")
    @IrpField(min = 15, max = 15, pattern = IrpPatterns.GSTIN)
    private String transId;

    @JsonProperty("TransName")
    @IrpField(min = 3, max = 100, pattern = IrpPatterns.NO_QUOTE)
    private String transName;

    @JsonProperty("TransMode")
    @IrpField(min = 1, max = 1, allowed = {"1", "2", "3", "4"})
    private String transMode;

    @JsonProperty("Distance")
    @IrpField(minVal = "0", maxVal = "4000", required = true)
    private BigDecimal distance;

    @JsonProperty("TransDocNo")
    @IrpField(min = 1, max = 15, pattern = IrpPatterns.TRANS_DOC_NO)
    private String transDocNo;

    @JsonProperty("TransDocDt")
    @IrpField(min = 10, max = 10, pattern = IrpPatterns.TRANS_DOC_DT)
    private String transDocDt;

    @JsonProperty("VehNo")
    @IrpField(min = 4, max = 20, pattern = IrpPatterns.VEH_NO)
    private String vehNo;

    @JsonProperty("VehType")
    @IrpField(min = 1, max = 1, allowed = {"O", "R"})
    private String vehType;

    public String getTransId() {
        return transId;
    }

    public void setTransId(String transId) {
        this.transId = transId;
    }

    public String getTransName() {
        return transName;
    }

    public void setTransName(String transName) {
        this.transName = transName;
    }

    public String getTransMode() {
        return transMode;
    }

    public void setTransMode(String transMode) {
        this.transMode = transMode;
    }

    public BigDecimal getDistance() {
        return distance;
    }

    public void setDistance(BigDecimal distance) {
        this.distance = distance;
    }

    public String getTransDocNo() {
        return transDocNo;
    }

    public void setTransDocNo(String transDocNo) {
        this.transDocNo = transDocNo;
    }

    public String getTransDocDt() {
        return transDocDt;
    }

    public void setTransDocDt(String transDocDt) {
        this.transDocDt = transDocDt;
    }

    public String getVehNo() {
        return vehNo;
    }

    public void setVehNo(String vehNo) {
        this.vehNo = vehNo;
    }

    public String getVehType() {
        return vehType;
    }

    public void setVehType(String vehType) {
        this.vehType = vehType;
    }
}