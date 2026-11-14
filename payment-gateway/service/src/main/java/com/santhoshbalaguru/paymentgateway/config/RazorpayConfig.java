package com.santhoshbalaguru.paymentgateway.config;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RazorpayConfig {

    @Bean
    RazorpayClient razorpayClient(RazorpayProperties p) throws RazorpayException {
        return new RazorpayClient(p.keyId(), p.keySecret());
    }
}