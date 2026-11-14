package com.example.idempiere.assistant.idempiere;

import com.example.idempiere.assistant.config.AssistantProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Thin client for the iDempiere REST API. Every data call carries the end user's own
 * role-scoped bearer token, so iDempiere enforces role, client and org security.
 * Tokens are never logged.
 */
@Component
public class IdempiereClient {

    private final RestClient rest;
    private final String verifyPath;

    public IdempiereClient(AssistantProperties props) {
        var cfg = props.idempiere();
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(cfg.timeout());
        factory.setReadTimeout(cfg.timeout());
        this.verifyPath = cfg.verifyPath();
        this.rest = RestClient.builder()
                .baseUrl(cfg.baseUrl())
                .requestFactory(factory)
                .defaultStatusHandler(
                        s -> s.value() == 401 || s.value() == 403,
                        (req, res) -> {
                            throw new IdempiereAuthException("iDempiere rejected the credentials or token");
                        })
                .build();
    }

    /** Step 1 of iDempiere login: user name and password. Returns a pre-context token. */
    public String login(String userName, String password) {
        JsonNode res = rest.post()
                .uri("/api/v1/auth/tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("userName", userName, "password", password))
                .retrieve()
                .body(JsonNode.class);
        return requireToken(res);
    }

    /** Step 2: pick client, role, org and warehouse. Returns the role-scoped token. */
    public String selectContext(String token, int clientId, int roleId, int orgId,
                                Integer warehouseId, String language) {
        Map<String, Object> body = new HashMap<>();
        body.put("clientId", clientId);
        body.put("roleId", roleId);
        body.put("organizationId", orgId);
        if (warehouseId != null) {
            body.put("warehouseId", warehouseId);
        }
        body.put("language", language);
        JsonNode res = rest.put()
                .uri("/api/v1/auth/tokens")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        return requireToken(res);
    }

    /** Cheap authenticated call used to check that a token is still valid. */
    public boolean verify(String token) {
        try {
            rest.get()
                    .uri(verifyPath)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (IdempiereAuthException e) {
            return false;
        }
    }

    /** Query a model, e.g. {@code c_order}, using iDempiere's OData-style parameters. */
    public JsonNode query(String token, String model, String filter, String select,
                          String orderBy, Integer top) {
        StringBuilder template = new StringBuilder("/api/v1/models/{model}");
        Map<String, Object> vars = new HashMap<>();
        vars.put("model", model);
        char sep = '?';
        if (filter != null) {
            template.append(sep).append("$filter={filter}");
            vars.put("filter", filter);
            sep = '&';
        }
        if (select != null) {
            template.append(sep).append("$select={select}");
            vars.put("select", select);
            sep = '&';
        }
        if (orderBy != null) {
            template.append(sep).append("$orderby={orderBy}");
            vars.put("orderBy", orderBy);
            sep = '&';
        }
        if (top != null) {
            template.append(sep).append("$top={top}");
            vars.put("top", top);
        }
        return rest.get()
                .uri(template.toString(), vars)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(JsonNode.class);
    }

    private static String requireToken(JsonNode res) {
        if (res == null || !res.hasNonNull("token")) {
            throw new IdempiereAuthException("iDempiere did not return a token");
        }
        return res.get("token").asText();
    }
}
