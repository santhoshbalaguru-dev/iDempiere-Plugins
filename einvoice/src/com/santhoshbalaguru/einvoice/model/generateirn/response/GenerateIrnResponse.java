package com.santhoshbalaguru.einvoice.model.generateirn.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GenerateIrnResponse {

    @JsonProperty("status_cd")
    private String statusCd;

    @JsonProperty("status_desc")
    private String statusDesc;

    @JsonProperty("data")
    private JsonNode data;

    @JsonProperty("error")
    private JsonNode error;             // shape varies, keep raw for logging

    public String getStatusCd() { return statusCd; }
    public void setStatusCd(String statusCd) { this.statusCd = statusCd; }

    public String getStatusDesc() { return statusDesc; }
    public void setStatusDesc(String statusDesc) { this.statusDesc = statusDesc; }

    public JsonNode getData() { return data; }
    public void setData(JsonNode data) { this.data = data; }

    public JsonNode getError() { return error; }
    public void setError(JsonNode error) { this.error = error; }

    public boolean isSuccess() {
        return "1".equals(statusCd);
    }
}