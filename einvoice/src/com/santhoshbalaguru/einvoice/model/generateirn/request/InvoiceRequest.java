package com.santhoshbalaguru.einvoice.model.generateirn.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class InvoiceRequest {

    @JsonProperty("Version")
    @IrpField(min = 1, max = 6, required = true)
    private String version;

    @JsonProperty("Irn")
    @IrpField(min = 64, max = 64)
    private String irn;

    @JsonProperty("TranDtls")
    @IrpField(required = true)
    private TranDtls tranDtls;

    @JsonProperty("DocDtls")
    @IrpField(required = true)
    private DocDtls docDtls;

    @JsonProperty("SellerDtls")
    @IrpField(required = true)
    private SellerDtls sellerDtls;

    @JsonProperty("BuyerDtls")
    @IrpField(required = true)
    private BuyerDtls buyerDtls;

    @JsonProperty("DispDtls")
    private DispDtls dispDtls;

    @JsonProperty("ShipDtls")
    private ShipDtls shipDtls;

    @JsonProperty("ItemList")
    @IrpField(required = true, maxItems = 1000)
    private List<ItemList> itemList;

    @JsonProperty("ValDtls")
    @IrpField(required = true)
    private ValDtls valDtls;

    @JsonProperty("PayDtls")
    private PayDtls payDtls;

    @JsonProperty("RefDtls")
    private RefDtls refDtls;

    @JsonProperty("AddlDocDtls")
    private List<AddlDocDtls> addlDocDtls;

    @JsonProperty("ExpDtls")
    private ExpDtls expDtls;

    @JsonProperty("EwbDtls")
    private EwbDtls ewbDtls;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getIrn() {
        return irn;
    }

    public void setIrn(String irn) {
        this.irn = irn;
    }

    public TranDtls getTranDtls() {
        return tranDtls;
    }

    public void setTranDtls(TranDtls tranDtls) {
        this.tranDtls = tranDtls;
    }

    public DocDtls getDocDtls() {
        return docDtls;
    }

    public void setDocDtls(DocDtls docDtls) {
        this.docDtls = docDtls;
    }

    public SellerDtls getSellerDtls() {
        return sellerDtls;
    }

    public void setSellerDtls(SellerDtls sellerDtls) {
        this.sellerDtls = sellerDtls;
    }

    public BuyerDtls getBuyerDtls() {
        return buyerDtls;
    }

    public void setBuyerDtls(BuyerDtls buyerDtls) {
        this.buyerDtls = buyerDtls;
    }

    public DispDtls getDispDtls() {
        return dispDtls;
    }

    public void setDispDtls(DispDtls dispDtls) {
        this.dispDtls = dispDtls;
    }

    public ShipDtls getShipDtls() {
        return shipDtls;
    }

    public void setShipDtls(ShipDtls shipDtls) {
        this.shipDtls = shipDtls;
    }

    public List<ItemList> getItemList() {
        return itemList;
    }

    public void setItemList(List<ItemList> itemList) {
        this.itemList = itemList;
    }

    public ValDtls getValDtls() {
        return valDtls;
    }

    public void setValDtls(ValDtls valDtls) {
        this.valDtls = valDtls;
    }

    public PayDtls getPayDtls() {
        return payDtls;
    }

    public void setPayDtls(PayDtls payDtls) {
        this.payDtls = payDtls;
    }

    public RefDtls getRefDtls() {
        return refDtls;
    }

    public void setRefDtls(RefDtls refDtls) {
        this.refDtls = refDtls;
    }

    public List<AddlDocDtls> getAddlDocDtls() {
        return addlDocDtls;
    }

    public void setAddlDocDtls(List<AddlDocDtls> addlDocDtls) {
        this.addlDocDtls = addlDocDtls;
    }

    public ExpDtls getExpDtls() {
        return expDtls;
    }

    public void setExpDtls(ExpDtls expDtls) {
        this.expDtls = expDtls;
    }

    public EwbDtls getEwbDtls() {
        return ewbDtls;
    }

    public void setEwbDtls(EwbDtls ewbDtls) {
        this.ewbDtls = ewbDtls;
    }
}