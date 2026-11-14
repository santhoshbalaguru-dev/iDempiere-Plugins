package com.santhoshbalaguru.paymentgateway.gateway;

public record PaymentEvent(String eventId, String eventType, Type type,
                           String gatewayLinkId, String gatewayPaymentId,
                           long amountPaidMinor, String currency) {

    public enum Type { PAID, EXPIRED, CANCELLED, IGNORED }
}