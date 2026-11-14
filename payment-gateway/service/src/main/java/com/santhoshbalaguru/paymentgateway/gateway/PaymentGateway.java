package com.santhoshbalaguru.paymentgateway.gateway;

public interface PaymentGateway {

    String code();

    GatewayLink createLink(LinkRequest request);

    void cancelLink(String gatewayLinkId);
}