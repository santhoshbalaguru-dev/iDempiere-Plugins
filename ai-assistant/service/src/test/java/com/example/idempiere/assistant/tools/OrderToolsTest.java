package com.example.idempiere.assistant.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.idempiere.assistant.chat.ChatService;
import com.example.idempiere.assistant.idempiere.IdempiereClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ToolContext;

class OrderToolsTest {

    private static final ToolContext CTX = new ToolContext(Map.of(ChatService.TOKEN_KEY, "user-token"));

    private static final String RESPONSE = """
            {"row-count":1,"records":[
              {"id":102,"DocumentNo":"800001","DateOrdered":"2026-11-02","DatePromised":"2026-11-20",
               "C_BPartner_ID":{"id":118,"identifier":"Seed Farm Inc."},
               "TotalLines":1000.00,"GrandTotal":1234.50,"IsSOTrx":false,
               "C_Currency_ID":{"id":100,"identifier":"USD"},
               "C_DocTypeTarget_ID":{"id":126,"identifier":"Standard Order"},
               "DocStatus":{"id":"CO","identifier":"Completed"}}
            ]}""";

    private final IdempiereClient client = mock(IdempiereClient.class);
    private final OrderTools tools = new OrderTools(client);

    @Test
    void looksUpByDocumentNoAndMapsFields() throws Exception {
        when(client.query(eq("user-token"), eq("c_order"), any(), any(), any(), eq(5)))
                .thenReturn(new ObjectMapper().readTree(RESPONSE));

        var result = tools.getOrderDetails("800001", null, null, CTX);

        var captor = ArgumentCaptor.forClass(String.class);
        verify(client).query(eq("user-token"), eq("c_order"), captor.capture(), any(), any(), eq(5));
        assertThat(captor.getValue()).isEqualTo("DocumentNo eq '800001'");

        assertThat(result.returned()).isEqualTo(1);
        var o = result.orders().get(0);
        assertThat(o.orderId()).isEqualTo(102);
        assertThat(o.orderType()).isEqualTo("purchase");
        assertThat(o.businessPartner()).isEqualTo("Seed Farm Inc.");
        assertThat(o.status()).isEqualTo("Completed");
        assertThat(o.grandTotal()).isEqualByComparingTo(new BigDecimal("1234.50"));
        assertThat(result.note()).isNull();
    }

    @Test
    void looksUpByOrderIdAndOrderType() throws Exception {
        when(client.query(any(), any(), any(), any(), any(), any()))
                .thenReturn(new ObjectMapper().readTree(RESPONSE));

        tools.getOrderDetails(null, 102, "Purchase", CTX);

        var captor = ArgumentCaptor.forClass(String.class);
        verify(client).query(any(), any(), captor.capture(), any(), any(), any());
        assertThat(captor.getValue()).isEqualTo("C_Order_ID eq 102 and IsSOTrx eq false");
    }

    @Test
    void emptyResultSaysNothingMatched() throws Exception {
        when(client.query(any(), any(), any(), any(), any(), any()))
                .thenReturn(new ObjectMapper().readTree("{\"row-count\":0,\"records\":[]}"));

        var result = tools.getOrderDetails("999999", null, null, CTX);

        assertThat(result.returned()).isZero();
        assertThat(result.note()).contains("No order matched");
    }

    @Test
    void rejectsUnsafeDocumentNumberWithoutCallingIdempiere() {
        var result = tools.getOrderDetails("1' or DocStatus eq 'DR", null, null, CTX);

        assertThat(result.returned()).isZero();
        assertThat(result.note()).contains("valid documentNo");
        verify(client, never()).query(any(), any(), any(), any(), any(), any());
    }

    @Test
    void requiresAnIdentifier() {
        var result = tools.getOrderDetails(null, null, null, CTX);

        assertThat(result.note()).contains("documentNo");
        verify(client, never()).query(any(), any(), any(), any(), any(), any());
    }
}
