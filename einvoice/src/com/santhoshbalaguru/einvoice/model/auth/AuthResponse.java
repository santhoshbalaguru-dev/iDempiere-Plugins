package com.santhoshbalaguru.einvoice.model.auth;
import com.fasterxml.jackson.annotation.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthResponse {

    private String irp;
    private AuthData data;
    private String status_cd;
    private String status_desc;

    public String getIrp() {
        return irp;
    }

    public AuthData getData() {
        return data;
    }

    public String getStatus_cd() {
        return status_cd;
    }

    public String getStatus_desc() {
        return status_desc;
    }
}
