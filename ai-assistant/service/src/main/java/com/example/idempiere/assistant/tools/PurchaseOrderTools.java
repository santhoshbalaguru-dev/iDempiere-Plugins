package com.example.idempiere.assistant.tools;

import com.example.idempiere.assistant.chat.ChatService;
import com.example.idempiere.assistant.idempiere.IdempiereClient;
import com.example.idempiere.assistant.idempiere.JsonText;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class PurchaseOrderTools {

    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderTools.class);

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    /** "Pending" here means the order is still Drafted (DR) or In Progress (IP). */
    private static final String PENDING_FILTER =
            "IsSOTrx eq false and (DocStatus eq 'DR' or DocStatus eq 'IP')";
    private static final String SELECT =
            "DocumentNo,DateOrdered,DatePromised,C_BPartner_ID,GrandTotal,C_Currency_ID,DocStatus";

    private final IdempiereClient idempiere;

    public PurchaseOrderTools(IdempiereClient idempiere) {
        this.idempiere = idempiere;
    }

    public record PurchaseOrderSummary(String documentNo, String dateOrdered, String datePromised,
                                       String vendor, BigDecimal grandTotal, String currency,
                                       String status) {}

    public record PendingPurchaseOrders(int returned, int totalMatching,
                                        List<PurchaseOrderSummary> orders) {}

    @Tool(name = "getPendingPurchaseOrders",
            description = "Lists purchase orders that are still pending, meaning Drafted or In Progress. "
                    + "Returns document number, order date, promised date, vendor, grand total, "
                    + "currency and status, newest first. Only shows orders the current user is allowed to see.")
    public PendingPurchaseOrders getPendingPurchaseOrders(
            @ToolParam(description = "Maximum number of orders to return, 1 to 50. Defaults to 20.",
                    required = false) Integer limit,
            ToolContext toolContext) {

        String token = (String) toolContext.getContext().get(ChatService.TOKEN_KEY);
        int top = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));

        JsonNode res = idempiere.query(token, "c_order", PENDING_FILTER, SELECT, "DateOrdered desc", top);

        List<PurchaseOrderSummary> orders = new ArrayList<>();
        for (JsonNode r : res.path("records")) {
            orders.add(new PurchaseOrderSummary(
                    JsonText.of(r, "DocumentNo"),
                    JsonText.of(r, "DateOrdered"),
                    JsonText.of(r, "DatePromised"),
                    JsonText.of(r, "C_BPartner_ID"),
                    r.path("GrandTotal").decimalValue(),
                    JsonText.of(r, "C_Currency_ID"),
                    JsonText.of(r, "DocStatus")));
        }
        int total = res.path("row-count").asInt(orders.size());
        log.info("tool=getPendingPurchaseOrders returned={} total={}", orders.size(), total);
        return new PendingPurchaseOrders(orders.size(), total, orders);
    }
}
