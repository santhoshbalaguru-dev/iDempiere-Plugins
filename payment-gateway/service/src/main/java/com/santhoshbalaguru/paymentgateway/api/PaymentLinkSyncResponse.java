package com.santhoshbalaguru.paymentgateway.api;

import com.santhoshbalaguru.paymentgateway.domain.PaymentLink;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentLinkSyncResponse(Long id, String gateway, String gatewayLinkId, Integer invoiceId,
                                      String status, BigDecimal amount, String currency,
                                      String gatewayPaymentId, OffsetDateTime paidAt,
                                      String errorMessage) {

    public static PaymentLinkSyncResponse from(PaymentLink l) {
        return new PaymentLinkSyncResponse(l.getId(), l.getGateway(), l.getGatewayLinkId(),
                l.getInvoiceId(), l.getStatus().name(), BigDecimal.valueOf(l.getAmountPaise(), 2),
                l.getCurrency(), l.getGatewayPaymentId(), l.getPaidAt(), l.getErrorMessage());
    }
}