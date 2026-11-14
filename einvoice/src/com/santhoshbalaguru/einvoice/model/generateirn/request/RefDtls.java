package com.santhoshbalaguru.einvoice.model.generateirn.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RefDtls {

    @JsonProperty("InvRm")
    @IrpField(min = 3, max = 100, pattern = IrpPatterns.NO_QUOTE)
    private String invRm;

    @JsonProperty("DocPerdDtls")
    private DocPerdDtls docPerdDtls;

    @JsonProperty("PrecDocDtls")
    private List<PrecDocDtls> precDocDtls;

    @JsonProperty("ContrDtls")
    private List<ContrDtls> contrDtls;

    public String getInvRm() {
        return invRm;
    }

    public void setInvRm(String invRm) {
        this.invRm = invRm;
    }

    public DocPerdDtls getDocPerdDtls() {
        return docPerdDtls;
    }

    public void setDocPerdDtls(DocPerdDtls docPerdDtls) {
        this.docPerdDtls = docPerdDtls;
    }

    public List<PrecDocDtls> getPrecDocDtls() {
        return precDocDtls;
    }

    public void setPrecDocDtls(List<PrecDocDtls> precDocDtls) {
        this.precDocDtls = precDocDtls;
    }

    public List<ContrDtls> getContrDtls() {
        return contrDtls;
    }

    public void setContrDtls(List<ContrDtls> contrDtls) {
        this.contrDtls = contrDtls;
    }
}