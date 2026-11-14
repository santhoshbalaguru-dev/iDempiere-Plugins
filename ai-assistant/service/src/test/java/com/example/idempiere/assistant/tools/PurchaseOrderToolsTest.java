package com.example.idempiere.assistant.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.idempiere.assistant.chat.ChatService;
import com.example.idempiere.assistant.idempiere.IdempiereClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

class PurchaseOrderToolsTest {

    private static final String RESPONSE = """
            {"page-count":1,"records-size":2,"row-count":1,"records":[
              {"DocumentNo":"800001","DateOrdered":"2026-11-02","DatePromised":"2026-11-20",
               "C_BPartner_ID":{"id":118,"identifier":"Seed Farm Inc.","model-name":"c_bpartner"},
               "GrandTotal":1234.50,
               "C_Currency_ID":{"id":100,"identifier":"USD","model-name":"c_currency"},
               "DocStatus":{"id":"IP","identifier":"In Progress","model-name":"ad_ref_list"}}
            ]}""";

    @Test
    void mapsRecordsAndForwardsTheUsersToken() throws Exception {
        IdempiereClient client = mock(IdempiereClient.class);
        when(client.query(eq("user-token"), eq("c_order"), any(), any(), any(), eq(5)))
                .thenReturn(new ObjectMapper().readTree(RESPONSE));

        var tools = new PurchaseOrderTools(client);
        var result = tools.getPendingPurchaseOrders(5,
                new ToolContext(Map.of(ChatService.TOKEN_KEY, "user-token")));

        assertThat(result.returned()).isEqualTo(1);
        assertThat(result.totalMatching()).isEqualTo(2);
        var po = result.orders().get(0);
        assertThat(po.documentNo()).isEqualTo("800001");
        assertThat(po.vendor()).isEqualTo("Seed Farm Inc.");
        assertThat(po.status()).isEqualTo("In Progress");
        assertThat(po.currency()).isEqualTo("USD");
        assertThat(po.grandTotal()).isEqualByComparingTo(new BigDecimal("1234.50"));
    }

    @Test
    void clampsTheLimit() throws Exception {
        IdempiereClient client = mock(IdempiereClient.class);
        when(client.query(any(), any(), any(), any(), any(), any()))
                .thenReturn(new ObjectMapper().readTree("{\"records\":[]}"));

        new PurchaseOrderTools(client).getPendingPurchaseOrders(
                10_000, new ToolContext(Map.of(ChatService.TOKEN_KEY, "t")));

        verify(client).query(eq("t"), eq("c_order"), any(), any(), any(), eq(50));
    }
}
