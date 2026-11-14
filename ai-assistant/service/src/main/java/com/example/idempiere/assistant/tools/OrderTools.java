package com.example.idempiere.assistant.tools;

import com.example.idempiere.assistant.chat.ChatService;
import com.example.idempiere.assistant.idempiere.IdempiereClient;
import com.example.idempiere.assistant.idempiere.JsonText;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class OrderTools {

    private static final Logger log = LoggerFactory.getLogger(OrderTools.class);

    private static final int MAX_MATCHES = 5;
    /** Document numbers are short; this also keeps quotes and operators out of the filter. */
    private static final Pattern SAFE_DOCUMENT_NO = Pattern.compile("[A-Za-z0-9._/\\- ]{1,30}");
    private static final String SELECT =
            "C_Order_ID,DocumentNo,DateOrdered,DatePromised,C_BPartner_ID,TotalLines,GrandTotal,"
                    + "C_Currency_ID,DocStatus,IsSOTrx,C_DocTypeTarget_ID,POReference";

    private final IdempiereClient idempiere;

    public OrderTools(IdempiereClient idempiere) {
        this.idempiere = idempiere;
    }

    public record OrderSummary(int orderId, String documentNo, String orderType, String documentType,
                               String dateOrdered, String datePromised, String businessPartner,
                               BigDecimal totalLines, BigDecimal grandTotal, String currency,
                               String status, String poReference) {}

    public record OrderLookupResult(int returned, int totalMatching, List<OrderSummary> orders,
                                    String note) {}

    @Tool(name = "getOrderDetails",
            description = "Looks up the header of a sales or purchase order, by document number or by "
                    + "internal order ID (C_Order_ID). Returns the order type, document type, dates, "
                    + "business partner, total of lines, grand total, currency and status. Use this for "
                    + "questions about one specific order. Provide documentNo or orderId. Only orders the "
                    + "current user may see are returned.")
    public OrderLookupResult getOrderDetails(
            @ToolParam(description = "The order's document number, for example 800001.",
                    required = false) String documentNo,
            @ToolParam(description = "The internal numeric C_Order_ID, when the user gives that instead.",
                    required = false) Integer orderId,
            @ToolParam(description = "Either 'sales' or 'purchase'. Leave out to search both.",
                    required = false) String orderType,
            ToolContext toolContext) {

        String token = (String) toolContext.getContext().get(ChatService.TOKEN_KEY);

        String idFilter;
        if (orderId != null) {
            idFilter = "C_Order_ID eq " + orderId;
        } else if (documentNo != null && SAFE_DOCUMENT_NO.matcher(documentNo.trim()).matches()) {
            idFilter = "DocumentNo eq '" + documentNo.trim() + "'";
        } else {
            return new OrderLookupResult(0, 0, List.of(),
                    "Provide a valid documentNo (letters, digits and . _ / - only) or a numeric orderId.");
        }

        String filter = idFilter;
        if (orderType != null && !orderType.isBlank()) {
            switch (orderType.trim().toLowerCase()) {
                case "sales" -> filter += " and IsSOTrx eq true";
                case "purchase" -> filter += " and IsSOTrx eq false";
                default -> {
                    return new OrderLookupResult(0, 0, List.of(),
                            "orderType must be 'sales' or 'purchase', or left out.");
                }
            }
        }

        JsonNode res = idempiere.query(token, "c_order", filter, SELECT, null, MAX_MATCHES);

        List<OrderSummary> orders = new ArrayList<>();
        for (JsonNode r : res.path("records")) {
            int id = r.path("id").asInt(r.path("C_Order_ID").asInt());
            orders.add(new OrderSummary(
                    id,
                    JsonText.of(r, "DocumentNo"),
                    r.path("IsSOTrx").asBoolean() ? "sales" : "purchase",
                    JsonText.of(r, "C_DocTypeTarget_ID"),
                    JsonText.of(r, "DateOrdered"),
                    JsonText.of(r, "DatePromised"),
                    JsonText.of(r, "C_BPartner_ID"),
                    r.path("TotalLines").decimalValue(),
                    r.path("GrandTotal").decimalValue(),
                    JsonText.of(r, "C_Currency_ID"),
                    JsonText.of(r, "DocStatus"),
                    JsonText.of(r, "POReference")));
        }
        int total = res.path("row-count").asInt(orders.size());
        log.info("tool=getOrderDetails returned={} total={}", orders.size(), total);

        String note = orders.isEmpty()
                ? "No order matched. It may not exist, or it may be outside what this user can access."
                : (total > orders.size() ? "More orders matched than are shown; ask the user to narrow it down." : null);
        return new OrderLookupResult(orders.size(), total, orders, note);
    }
}
