package com.santhoshbalaguru.paymentgateway.gateway.razorpay;

import com.santhoshbalaguru.paymentgateway.config.RazorpayProperties;
import com.santhoshbalaguru.paymentgateway.gateway.InvalidSignatureException;
import com.santhoshbalaguru.paymentgateway.gateway.PaymentEvent;
import com.santhoshbalaguru.paymentgateway.gateway.WebhookAdapter;
import lombok.RequiredArgsConstructor;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RazorpayWebhookAdapter implements WebhookAdapter {

    private final RazorpayProperties props;

    @Override
    public String code() {
        return "razorpay";
    }

    @Override
    public PaymentEvent verifyAndParse(byte[] rawBody, Map<String, String> headers) {
        verifySignature(rawBody, headers.get("x-razorpay-signature"));
        try {
            JSONObject root = new JSONObject(new String(rawBody, StandardCharsets.UTF_8));
            String event = root.optString("event", "");

            String eventId = headers.get("x-razorpay-event-id");
            if (eventId == null || eventId.isBlank()) {
                eventId = "sha256-" + sha256Hex(rawBody);
            }

            PaymentEvent.Type type = switch (event) {
                case "payment_link.paid" -> PaymentEvent.Type.PAID;
                case "payment_link.expired" -> PaymentEvent.Type.EXPIRED;
                case "payment_link.cancelled" -> PaymentEvent.Type.CANCELLED;
                default -> PaymentEvent.Type.IGNORED;
            };
            if (type == PaymentEvent.Type.IGNORED) {
                return new PaymentEvent(eventId, event, type, null, null, 0, null);
            }

            JSONObject payload = root.getJSONObject("payload");
            String linkId = payload.getJSONObject("payment_link")
                    .getJSONObject("entity").getString("id");

            String paymentId = null;
            long paidMinor = 0;
            String currency = null;
            if (type == PaymentEvent.Type.PAID) {
                JSONObject pay = payload.getJSONObject("payment").getJSONObject("entity");
                paymentId = pay.getString("id");
                paidMinor = pay.getLong("amount");
                currency = pay.getString("currency");
            }
            return new PaymentEvent(eventId, event, type, linkId, paymentId, paidMinor, currency);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Malformed Razorpay webhook payload: " + e.getMessage());
        }
    }

    private void verifySignature(byte[] rawBody, String signature) {
        String secret = props.webhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new InvalidSignatureException("Webhook secret is not configured");
        }
        if (signature == null) {
            throw new InvalidSignatureException("Missing signature");
        }
        byte[] expected = hmacHex(secret, rawBody).getBytes(StandardCharsets.UTF_8);
        byte[] actual = signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new InvalidSignatureException("Signature mismatch");
        }
    }

    private static String hmacHex(String secret, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}