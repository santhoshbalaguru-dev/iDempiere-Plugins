package com.santhoshbalaguru.paymentgateway.api;

import com.santhoshbalaguru.paymentgateway.domain.LinkStatus;
import com.santhoshbalaguru.paymentgateway.service.PaymentLinkSyncService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/payment-links")
@RequiredArgsConstructor
public class PaymentLinkSyncController {

    private final PaymentLinkSyncService service;

    public record SyncedRequest(@NotNull LinkStatus status, Integer erpPaymentId) {
    }

    @GetMapping("/unsynced")
    public List<PaymentLinkSyncResponse> unsynced(@RequestParam int adClientId,
                                                  @RequestParam(defaultValue = "0") long afterId,
                                                  @RequestParam(defaultValue = "50") int limit) {
        int capped = Math.min(Math.max(limit, 1), 200);
        return service.unsynced(adClientId, afterId, capped).stream().map(PaymentLinkSyncResponse::from).toList();
    }

    @PostMapping("/{id}/synced")
    public Map<String, String> synced(@PathVariable Long id, @Valid @RequestBody SyncedRequest req) {
        service.markSynced(id, req.status(), req.erpPaymentId());
        return Map.of("status", "ok");
    }
}