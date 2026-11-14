package com.santhoshbalaguru.paymentgateway.gateway;

import java.util.Map;

public interface WebhookAdapter {

    String code();

    /**
     * Verifies the gateway's signature over the raw body and converts the call
     * into a common event. Header names in the map are lower-case.
     *
     * @throws InvalidSignatureException if the signature is missing or wrong
     */
    PaymentEvent verifyAndParse(byte[] rawBody, Map<String, String> headers);
}