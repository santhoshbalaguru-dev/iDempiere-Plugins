package com.santhoshbalaguru.paymentgateway.api;

import com.santhoshbalaguru.paymentgateway.domain.PaymentLink;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentLinkResponse(Long id, String gateway, String gatewayLinkId,
                                  String shortUrl, String status, BigDecimal amount,
                                  String currency, OffsetDateTime expireBy,
                                  String gatewayPaymentId, OffsetDateTime paidAt,
                                  boolean reused) {

    public static PaymentLinkResponse from(PaymentLink l, boolean reused) {
        return new PaymentLinkResponse(l.getId(), l.getGateway(), l.getGatewayLinkId(),
                l.getShortUrl(), l.getStatus().name(), BigDecimal.valueOf(l.getAmountPaise(), 2),
                l.getCurrency(), l.getExpireBy(), l.getGatewayPaymentId(), l.getPaidAt(), reused);
    }
}