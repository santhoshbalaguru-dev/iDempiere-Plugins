package com.santhoshbalaguru.paymentgateway.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CreatePaymentLinkRequest(
        @NotNull Integer adClientId,
        @NotNull Integer adOrgId,
        @NotNull Integer invoiceId,
        @NotBlank @Size(max = 60) String invoiceDocumentNo,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotBlank String customerName,
        @NotBlank @Email String customerEmail,
        String customerPhone,
        @NotNull OffsetDateTime expireBy,
        @Size(max = 255) String description,
        @Pattern(regexp = "[a-z]{3,30}") String gateway,
        Boolean forceNew) {
}