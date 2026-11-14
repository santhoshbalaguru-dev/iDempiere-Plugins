package com.santhoshbalaguru.paymentgateway.gateway;

import com.santhoshbalaguru.paymentgateway.config.GatewayProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class GatewayRegistry {

    private final Map<String, PaymentGateway> byCode;
    private final String defaultCode;

    public GatewayRegistry(List<PaymentGateway> gateways, GatewayProperties props) {
        this.byCode = gateways.stream()
                .collect(Collectors.toMap(PaymentGateway::code, Function.identity()));
        this.defaultCode = props.defaultGateway();
        if (!byCode.containsKey(defaultCode)) {
            throw new IllegalStateException("Default gateway not available: " + defaultCode);
        }
    }

    public PaymentGateway get(String code) {
        String c = (code == null || code.isBlank()) ? defaultCode : code.toLowerCase();
        PaymentGateway g = byCode.get(c);
        if (g == null) {
            throw new IllegalArgumentException("Unsupported gateway: " + c);
        }
        return g;
    }
}