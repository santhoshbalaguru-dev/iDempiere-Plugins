package com.santhoshbalaguru.paymentgateway.gateway;

import java.time.OffsetDateTime;
import java.util.Map;

public record LinkRequest(String referenceId, long amountMinor, String currency,
                          String description, String customerName, String customerEmail,
                          String customerPhone, OffsetDateTime expireBy,
                          Map<String, String> notes) {
}