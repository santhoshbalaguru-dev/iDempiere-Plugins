package com.santhoshbalaguru.einvoice.model.generateirn.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GenerateIrnData {

    @JsonProperty("AckNo")
    private Long ackNo;

    @JsonProperty("AckDt")
    private String ackDt;               // "yyyy-MM-dd HH:mm:ss"

    @JsonProperty("Irn")
    private String irn;

    @JsonProperty("SignedInvoice")
    private String signedInvoice;       // JWT, several KB

    @JsonProperty("SignedQRCode")
    private String signedQRCode;        // JWT, ~1 KB

    @JsonProperty("Status")
    private String status;              // "ACT" = active

    @JsonProperty("EwbNo")
    private Long ewbNo;

    @JsonProperty("EwbDt")
    private String ewbDt;

    @JsonProperty("EwbValidTill")
    private String ewbValidTill;

    @JsonProperty("Remarks")
    private String remarks;

    public Long getAckNo() { return ackNo; }
    public void setAckNo(Long ackNo) { this.ackNo = ackNo; }

    public String getAckDt() { return ackDt; }
    public void setAckDt(String ackDt) { this.ackDt = ackDt; }

    public String getIrn() { return irn; }
    public void setIrn(String irn) { this.irn = irn; }

    public String getSignedInvoice() { return signedInvoice; }
    public void setSignedInvoice(String signedInvoice) { this.signedInvoice = signedInvoice; }

    public String getSignedQRCode() { return signedQRCode; }
    public void setSignedQRCode(String signedQRCode) { this.signedQRCode = signedQRCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getEwbNo() { return ewbNo; }
    public void setEwbNo(Long ewbNo) { this.ewbNo = ewbNo; }

    public String getEwbDt() { return ewbDt; }
    public void setEwbDt(String ewbDt) { this.ewbDt = ewbDt; }

    public String getEwbValidTill() { return ewbValidTill; }
    public void setEwbValidTill(String ewbValidTill) { this.ewbValidTill = ewbValidTill; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}