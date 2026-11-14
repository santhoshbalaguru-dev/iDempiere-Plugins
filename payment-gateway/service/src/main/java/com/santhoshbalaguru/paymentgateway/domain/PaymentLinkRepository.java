package com.santhoshbalaguru.paymentgateway.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentLinkRepository extends JpaRepository<PaymentLink, Long> {

    Optional<PaymentLink> findByInvoiceIdAndStatus(Integer invoiceId, LinkStatus status);

    Optional<PaymentLink> findByGatewayAndGatewayLinkId(String gateway, String gatewayLinkId);

    boolean existsByInvoiceIdAndStatusInAndErpPostedAtIsNull(Integer invoiceId,
                                                             Collection<LinkStatus> statuses);

    Optional<PaymentLink> findFirstByInvoiceIdAndStatusNotOrderByIdDesc(Integer invoiceId,
                                                                        LinkStatus status);

    @Query("""
            select l from PaymentLink l
             where l.adClientId = :clientId
               and l.id > :afterId
               and l.status in :statuses
               and (l.erpSyncedStatus is null or l.erpSyncedStatus <> l.status)
             order by l.id
            """)
    List<PaymentLink> findUnsynced(@Param("clientId") Integer clientId,
                                   @Param("afterId") Long afterId,
                                   @Param("statuses") Collection<LinkStatus> statuses,
                                   Pageable page);

    /**
     * Bulk update, so @PreUpdate does not run and updatedAt is set here. Only CREATED links are
     * touched; the WHERE is evaluated again after a row lock, so a payment that just committed wins.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            update PaymentLink l
               set l.status = :expired, l.updatedAt = :now
             where l.status = :created
               and l.expireBy is not null
               and l.expireBy < :cutoff
            """)
    int expireOverdue(@Param("created") LinkStatus created,
                      @Param("expired") LinkStatus expired,
                      @Param("now") OffsetDateTime now,
                      @Param("cutoff") OffsetDateTime cutoff);
}