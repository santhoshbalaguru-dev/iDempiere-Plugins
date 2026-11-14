package com.santhoshbalaguru.paymentgateway.gateway.razorpay;

import com.razorpay.PaymentLink;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.santhoshbalaguru.paymentgateway.gateway.GatewayException;
import com.santhoshbalaguru.paymentgateway.gateway.GatewayLink;
import com.santhoshbalaguru.paymentgateway.gateway.LinkRequest;
import com.santhoshbalaguru.paymentgateway.gateway.PaymentGateway;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RazorpayGateway implements PaymentGateway {

    private final RazorpayClient client;

    @Override
    public String code() {
        return "razorpay";
    }

    @Override
    public GatewayLink createLink(LinkRequest r) {
        try {
            JSONObject body = new JSONObject();
            body.put("amount", r.amountMinor());
            body.put("currency", r.currency());
            body.put("accept_partial", false);
            body.put("reference_id", r.referenceId());
            body.put("description", r.description());
            body.put("expire_by", r.expireBy().toEpochSecond());
            body.put("reminder_enable", false);

            JSONObject customer = new JSONObject();
            customer.put("name", r.customerName());
            customer.put("email", r.customerEmail());
            if (r.customerPhone() != null && !r.customerPhone().isBlank()) {
                customer.put("contact", r.customerPhone());
            }
            body.put("customer", customer);

            JSONObject notify = new JSONObject();
            notify.put("email", false);
            notify.put("sms", false);
            body.put("notify", notify);

            body.put("notes", new JSONObject(r.notes()));

            PaymentLink link = client.paymentLink.create(body);
            String id = link.get("id");
            String url = link.get("short_url");
            return new GatewayLink(id, url);
        } catch (RazorpayException e) {
            throw new GatewayException("Razorpay error: " + e.getMessage(), e);
        }
    }

    @Override
    public void cancelLink(String gatewayLinkId) {
        try {
            client.paymentLink.cancel(gatewayLinkId);
        } catch (RazorpayException e) {
            throw new GatewayException("Razorpay cancel failed: " + e.getMessage(), e);
        }
    }
}