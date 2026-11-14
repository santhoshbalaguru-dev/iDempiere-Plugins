package com.santhoshbalaguru.paymentgateway.service;

import com.santhoshbalaguru.paymentgateway.domain.LinkStatus;
import com.santhoshbalaguru.paymentgateway.domain.PaymentLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Backup for a lost payment_link.expired webhook: CREATED links past their expiry become EXPIRED,
 * and Sync Payment Links then reports them to iDempiere like any other status change.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LinkExpiryService {

    /** Wait this long after expireBy so a payment webhook that is still on its way is processed first. */
    static final Duration GRACE = Duration.ofMinutes(10);

    private final PaymentLinkRepository repo;

    @Scheduled(fixedDelayString = "${gateway.expiry-sweep-ms:300000}",
               initialDelayString = "${gateway.expiry-sweep-initial-ms:60000}")
    @Transactional
    public void sweep() {
        expireOverdue(OffsetDateTime.now());
    }

    int expireOverdue(OffsetDateTime now) {
        int n = repo.expireOverdue(LinkStatus.CREATED, LinkStatus.EXPIRED, now, now.minus(GRACE));
        if (n > 0) {
            log.info("Marked {} overdue payment link(s) as EXPIRED", n);
        }
        return n;
    }
}