package com.santhoshbalaguru.paymentgateway.service;

import com.santhoshbalaguru.paymentgateway.api.CreatePaymentLinkRequest;
import com.santhoshbalaguru.paymentgateway.domain.LinkStatus;
import com.santhoshbalaguru.paymentgateway.domain.PaymentLink;
import com.santhoshbalaguru.paymentgateway.domain.PaymentLinkRepository;
import com.santhoshbalaguru.paymentgateway.gateway.GatewayLink;
import com.santhoshbalaguru.paymentgateway.gateway.GatewayRegistry;
import com.santhoshbalaguru.paymentgateway.gateway.LinkRequest;
import com.santhoshbalaguru.paymentgateway.gateway.PaymentGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentLinkService {

    private final PaymentLinkRepository repo;
    private final GatewayRegistry gateways;

    public record Result(PaymentLink link, boolean reused) {
    }

    public Result createOrReuse(CreatePaymentLinkRequest req) {
        PaymentGateway gateway = gateways.get(req.gateway());
        long amountMinor = toMinor(req.amount());
        if (req.expireBy().isBefore(OffsetDateTime.now().plusMinutes(20))) {
            throw new IllegalArgumentException("expireBy must be at least 20 minutes from now");
        }
        boolean forceNew = Boolean.TRUE.equals(req.forceNew());

        // at most one row: uq_payment_link_active_invoice (V2 migration) covers CREATING and CREATED

        repo.findByInvoiceIdAndStatus(req.invoiceId(), LinkStatus.CREATING).ifPresent(stuck -> {
            if (stuck.getCreatedAt().isBefore(OffsetDateTime.now().minusMinutes(5))) {
                stuck.setStatus(LinkStatus.FAILED);
                stuck.setErrorMessage("Abandoned while creating");
                repo.saveAndFlush(stuck);
            }
        });

        // money received and not yet posted in iDempiere: never create another link
        if (repo.existsByInvoiceIdAndStatusInAndErpPostedAtIsNull(
                req.invoiceId(), List.of(LinkStatus.PAID))) {
            throw new ConflictException(
                    "A payment was already received for this invoice and is waiting to be posted in iDempiere");
        }

        // a payment of a different amount needs a person's decision; Regenerate is that decision
        repo.findFirstByInvoiceIdAndStatusNotOrderByIdDesc(req.invoiceId(), LinkStatus.FAILED)
                .filter(l -> l.getStatus() == LinkStatus.AMOUNT_MISMATCH)
                .ifPresent(l -> requireMismatchReviewed(l, forceNew));

        Optional<PaymentLink> existing =
                repo.findByInvoiceIdAndStatus(req.invoiceId(), LinkStatus.CREATED);
        if (existing.isPresent()) {
            PaymentLink old = existing.get();
            boolean expired = old.getExpireBy() != null
                    && old.getExpireBy().isBefore(OffsetDateTime.now());
            boolean sameGateway = old.getGateway().equals(gateway.code());
            if (!forceNew && !expired && sameGateway && old.getAmountPaise() == amountMinor) {
                return new Result(old, true);
            }
            if (expired) {
                old.setStatus(LinkStatus.EXPIRED);
            } else {
                // cancel through the gateway that created it; fail loudly, never leave two live links
                gateways.get(old.getGateway()).cancelLink(old.getGatewayLinkId());
                old.setStatus(LinkStatus.CANCELLED);
            }
            repo.saveAndFlush(old);
        }

        PaymentLink row = new PaymentLink();
        row.setGateway(gateway.code());
        row.setAdClientId(req.adClientId());
        row.setAdOrgId(req.adOrgId());
        row.setInvoiceId(req.invoiceId());
        row.setInvoiceDocumentNo(req.invoiceDocumentNo());
        row.setAmountPaise(amountMinor);
        row.setCurrency(req.currency());
        row.setExpireBy(req.expireBy());
        row.setStatus(LinkStatus.CREATING);
        try {
            row = repo.saveAndFlush(row);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("A payment link is already being created for this invoice");
        }

        try {
            GatewayLink gl = gateway.createLink(new LinkRequest(
                    "INV" + req.invoiceId() + "-" + row.getId(),
                    amountMinor, req.currency(), req.description(),
                    req.customerName(), req.customerEmail(), req.customerPhone(),
                    req.expireBy(),
                    Map.of("ad_client_id", String.valueOf(req.adClientId()),
                            "ad_org_id", String.valueOf(req.adOrgId()),
                            "invoice_id", String.valueOf(req.invoiceId()),
                            "invoice_no", req.invoiceDocumentNo(),
                            "link_row_id", String.valueOf(row.getId()))));
            row.setGatewayLinkId(gl.id());
            row.setShortUrl(gl.shortUrl());
            row.setStatus(LinkStatus.CREATED);
        } catch (RuntimeException e) {
            row.setStatus(LinkStatus.FAILED);
            row.setErrorMessage(e.getMessage());
            repo.save(row);
            throw e;
        }
        return new Result(repo.save(row), false);
    }

    /**
     * The latest link of the invoice was paid with a different amount. iDempiere must have recorded
     * it first (erpSyncedStatus), and a new link is only issued on an explicit Regenerate.
     */
    private static void requireMismatchReviewed(PaymentLink l, boolean forceNew) {
        if (l.getErpSyncedStatus() != LinkStatus.AMOUNT_MISMATCH) {
            throw new ConflictException("A payment of a different amount was received for this invoice "
                    + "and iDempiere has not recorded it yet; run Sync Payment Links first");
        }
        if (!forceNew) {
            throw new ConflictException("A payment of a different amount was received for this invoice ("
                    + l.getErrorMessage() + "). Review it in iDempiere, then use Regenerate to issue a new link");
        }
    }

    private static long toMinor(BigDecimal amount) {
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("amount can have at most 2 decimal places");
        }
    }
}