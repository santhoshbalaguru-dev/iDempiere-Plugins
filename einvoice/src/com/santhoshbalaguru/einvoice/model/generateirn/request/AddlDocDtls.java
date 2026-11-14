package com.santhoshbalaguru.einvoice.model.generateirn.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AddlDocDtls {

    @JsonProperty("Url")
    @IrpField(min = 3, max = 100, pattern = IrpPatterns.NO_QUOTE)
    private String url;

    @JsonProperty("Docs")
    @IrpField(min = 3, max = 1000, pattern = IrpPatterns.NO_QUOTE)
    private String docs;

    @JsonProperty("Info")
    @IrpField(min = 3, max = 1000, pattern = IrpPatterns.NO_QUOTE)
    private String info;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDocs() {
        return docs;
    }

    public void setDocs(String docs) {
        this.docs = docs;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }
}