package com.santhoshbalaguru.paymentgateway.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "razorpay")
public record RazorpayProperties(@NotBlank String keyId, @NotBlank String keySecret, String webhookSecret) {
}