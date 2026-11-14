package com.santhoshbalaguru.paymentgateway.service;

import com.santhoshbalaguru.paymentgateway.domain.LinkStatus;
import com.santhoshbalaguru.paymentgateway.domain.PaymentLink;
import com.santhoshbalaguru.paymentgateway.domain.PaymentLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentLinkSyncService {

    private static final List<LinkStatus> REPORTED = List.of(
            LinkStatus.PAID, LinkStatus.AMOUNT_MISMATCH, LinkStatus.EXPIRED, LinkStatus.CANCELLED);

    private final PaymentLinkRepository repo;

    @Transactional(readOnly = true)
    public List<PaymentLink> unsynced(int adClientId, long afterId, int limit) {
        return repo.findUnsynced(adClientId, afterId, REPORTED, PageRequest.of(0, limit));
    }

    /** Idempotent: iDempiere may confirm the same link again after a failed earlier call. */
    @Transactional
    public void markSynced(Long id, LinkStatus status, Integer erpPaymentId) {
        PaymentLink link = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        link.setErpSyncedStatus(status);
        if (erpPaymentId != null) {
            link.setErpPaymentId(erpPaymentId);
            if (link.getErpPostedAt() == null) {
                link.setErpPostedAt(OffsetDateTime.now());
            }
        }
        repo.save(link);
    }
}