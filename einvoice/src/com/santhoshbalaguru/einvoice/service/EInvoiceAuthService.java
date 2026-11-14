package com.santhoshbalaguru.einvoice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.santhoshbalaguru.einvoice.client.EInvoiceApiClient;
import com.santhoshbalaguru.einvoice.config.EInvoiceConfig;
import com.santhoshbalaguru.einvoice.model.auth.AuthData;
import com.santhoshbalaguru.einvoice.model.auth.AuthResponse;

public class EInvoiceAuthService {

    private final EInvoiceApiClient apiClient;
    private final ObjectMapper objectMapper;

    private AuthResponse authResponse;
    private AuthData authData;

    public EInvoiceAuthService(EInvoiceConfig cfg) {
        this.apiClient = new EInvoiceApiClient(cfg);
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Authenticate with the E-Invoice API.
     *
     * @return AuthResponse
     * @throws Exception if authentication fails
     */
    
    public AuthResponse authenticate() throws Exception {

        // Drop any state from an earlier call, so a failure can't leave a stale token behind
        authResponse = null;
        authData = null;

        String response = apiClient.authenticate();

        if (response == null || response.trim().isEmpty())
            throw new Exception("Empty response received from E-Invoice authentication API");

        try {
            authResponse = objectMapper.readValue(response, AuthResponse.class);
        } catch (JsonProcessingException e) {
            throw new Exception("Could not parse the authentication response ("
                    + e.getOriginalMessage() + "). Response was: " + snippet(response), e);
        }

        if (!"1".equals(authResponse.getStatus_cd()))
            throw new Exception("E-Invoice authentication failed: "
                    + (authResponse.getStatus_desc() != null ? authResponse.getStatus_desc() : snippet(response)));

        authData = authResponse.getData();

        if (authData == null)
            throw new Exception("Authentication successful but authentication data is missing");

        if (authData.getAuthToken() == null || authData.getAuthToken().trim().isEmpty())
            throw new Exception("Authentication successful but AuthToken is missing");

        return authResponse;
    }

    /** First 300 characters of a response, whitespace collapsed, for error messages. */
    private static String snippet(String s) {
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > 300 ? t.substring(0, 300) + "..." : t;
    }
    

    /**
     * Get complete authentication response.
     */
    public AuthResponse getAuthResponse() {
        return authResponse;
    }

    /**
     * Get authentication data.
     */
    public AuthData getAuthData() {
        return authData;
    }

    /**
     * Get authentication token.
     */
    public String getAuthToken() {

        if (!isAuthenticated()) {
            throw new IllegalStateException(
                "E-Invoice authentication has not been completed"
            );
        }

        return authData.getAuthToken();
    }

    /**
     * Get authenticated client ID.
     */
    public String getClientId() {

        if (authData == null) {
            return null;
        }

        return authData.getClientId();
    }

    /**
     * Get authenticated username.
     */
    public String getUserName() {

        if (authData == null) {
            return null;
        }

        return authData.getUserName();
    }

    /**
     * Check whether authentication is available.
     */
    public boolean isAuthenticated() {

        return authData != null
                && authData.getAuthToken() != null
                && !authData.getAuthToken().trim().isEmpty();
    }

    /**
     * Clear authentication information.
     */
    public void clearAuthentication() {

        authResponse = null;
        authData = null;
    }
}