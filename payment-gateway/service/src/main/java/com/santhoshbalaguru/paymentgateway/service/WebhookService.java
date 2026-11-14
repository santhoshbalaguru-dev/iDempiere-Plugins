package com.santhoshbalaguru.paymentgateway.service;

import com.santhoshbalaguru.paymentgateway.domain.LinkStatus;
import com.santhoshbalaguru.paymentgateway.domain.PaymentLink;
import com.santhoshbalaguru.paymentgateway.domain.PaymentLinkRepository;
import com.santhoshbalaguru.paymentgateway.domain.ProcessedWebhookEventRepository;
import com.santhoshbalaguru.paymentgateway.gateway.PaymentEvent;
import com.santhoshbalaguru.paymentgateway.gateway.WebhookAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;


@Slf4j
@Service
public class WebhookService {

    public enum Outcome { PROCESSED, DUPLICATE, IGNORED }

    private final Map<String, WebhookAdapter> adapters;
    private final ProcessedWebhookEventRepository events;
    private final PaymentLinkRepository links;

    public WebhookService(List<WebhookAdapter> adapters,
                          ProcessedWebhookEventRepository events,
                          PaymentLinkRepository links) {
        this.adapters = adapters.stream()
                .collect(Collectors.toMap(WebhookAdapter::code, Function.identity()));
        this.events = events;
        this.links = links;
    }

    public boolean supports(String gateway) {
        return adapters.containsKey(gateway.toLowerCase());
    }

    /**
     * One transaction: if anything fails after the event was recorded, the record is
     * rolled back too, so the gateway's retry is processed again instead of skipped.
     */
    @Transactional
    public Outcome handle(String gateway, byte[] rawBody, Map<String, String> headers) {
        String code = gateway.toLowerCase();
        PaymentEvent ev = adapters.get(code).verifyAndParse(rawBody, headers);

        if (ev.type() == PaymentEvent.Type.IGNORED) {
            log.debug("Ignoring {} event {}", code, ev.eventType());
            return Outcome.IGNORED;
        }
        if (events.insertIfAbsent(code, ev.eventId(), ev.eventType()) == 0) {
            log.info("Duplicate {} event {}", code, ev.eventId());
            return Outcome.DUPLICATE;
        }

        Optional<PaymentLink> found = links.findByGatewayAndGatewayLinkId(code, ev.gatewayLinkId());
        if (found.isEmpty()) {
            if (ev.type() == PaymentEvent.Type.PAID) {
                // money for a link we do not know: fail, so the event record is rolled back
                // and the gateway's retry is processed instead of skipped as a duplicate
                log.error("{} PAID event {} refers to unknown link {}", code, ev.eventId(), ev.gatewayLinkId());
                throw new UnknownLinkException("Payment for unknown link " + ev.gatewayLinkId());
            }
            log.warn("{} event {} refers to unknown link {}", code, ev.eventId(), ev.gatewayLinkId());
            return Outcome.IGNORED;
        }

        PaymentLink link = found.get();
        switch (ev.type()) {
            case PAID -> applyPaid(link, ev);
            case EXPIRED -> {
                if (link.getStatus() == LinkStatus.CREATED) {
                    link.setStatus(LinkStatus.EXPIRED);
                }
            }
            case CANCELLED -> {
                if (link.getStatus() == LinkStatus.CREATED) {
                    link.setStatus(LinkStatus.CANCELLED);
                }
            }
            default -> { }
        }
        links.save(link);
        log.info("Applied {} event {} to link {} (now {})",
                code, ev.eventType(), link.getId(), link.getStatus());
        return Outcome.PROCESSED;
    }

    private void applyPaid(PaymentLink link, PaymentEvent ev) {
        if (link.getStatus() == LinkStatus.PAID || link.getStatus() == LinkStatus.AMOUNT_MISMATCH) {
            log.info("Link {} already {}, ignoring second payment event", link.getId(), link.getStatus());
            return;
        }
        LinkStatus before = link.getStatus();
        link.setGatewayPaymentId(ev.gatewayPaymentId());
        link.setPaidAt(OffsetDateTime.now());

        boolean matches = ev.amountPaidMinor() == link.getAmountPaise()
                && link.getCurrency().equals(ev.currency());
        if (!matches) {
            link.setStatus(LinkStatus.AMOUNT_MISMATCH);
            link.setErrorMessage("Paid " + ev.amountPaidMinor() + " " + ev.currency()
                    + ", expected " + link.getAmountPaise() + " " + link.getCurrency());
            return;
        }
        link.setStatus(LinkStatus.PAID);
        if (before != LinkStatus.CREATED) {
            link.setErrorMessage("Payment received while link was " + before + "; review needed");
        }
    }
}