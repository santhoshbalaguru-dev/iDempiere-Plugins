package com.santhoshbalaguru.einvoice.model.auth;
import com.fasterxml.jackson.annotation.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthData {
	
	@JsonProperty("TokenExpiry")
    private String tokenExpiry;
	
	@JsonProperty("Sek")
    private String sek;
	
	@JsonProperty("clientId")
    private String clientId;
	
	@JsonProperty("userName")
    private String userName;
	
	@JsonProperty("AuthToken")
    private String authToken;

	public String getTokenExpiry() {
        return tokenExpiry;
    }

    public void setTokenExpiry(String tokenExpiry) {
        this.tokenExpiry = tokenExpiry;
    }

    public String getSek() {
        return sek;
    }

    public void setSek(String sek) {
        this.sek = sek;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }
}
