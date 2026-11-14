package com.santhoshbalaguru.paymentgateway.api;

import com.santhoshbalaguru.paymentgateway.service.WebhookService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService service;

    @PostMapping("/{gateway}")
    public ResponseEntity<Map<String, String>> receive(@PathVariable String gateway,
                                                       @RequestBody byte[] rawBody,
                                                       HttpServletRequest request) {
        if (!service.supports(gateway)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Map<String, String> headers = new HashMap<>();
        for (String name : Collections.list(request.getHeaderNames())) {
            headers.put(name.toLowerCase(), request.getHeader(name));
        }
        var outcome = service.handle(gateway, rawBody, headers);
        return ResponseEntity.ok(Map.of("status", outcome.name().toLowerCase()));
    }
}