package com.santhoshbalaguru.paymentgateway.api;

import com.santhoshbalaguru.paymentgateway.domain.PaymentLinkRepository;
import com.santhoshbalaguru.paymentgateway.service.PaymentLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/payment-links")
@RequiredArgsConstructor
public class PaymentLinkController {

    private final PaymentLinkService service;
    private final PaymentLinkRepository repo;

    @PostMapping
    public PaymentLinkResponse create(@Valid @RequestBody CreatePaymentLinkRequest req) {
        var result = service.createOrReuse(req);
        return PaymentLinkResponse.from(result.link(), result.reused());
    }

    @GetMapping("/{id}")
    public PaymentLinkResponse get(@PathVariable Long id) {
        return repo.findById(id)
                .map(l -> PaymentLinkResponse.from(l, false))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}