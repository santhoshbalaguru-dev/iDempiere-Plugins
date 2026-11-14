package com.santhoshbalaguru.paymentgateway.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "payment_link")
@Getter
@Setter
@NoArgsConstructor
public class PaymentLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String gateway;
    private Integer adClientId;
    private Integer adOrgId;
    private Integer invoiceId;
    private String invoiceDocumentNo;
    private String gatewayLinkId;
    private String shortUrl;
    private long amountPaise;
    private String currency;
    private OffsetDateTime paidAt;
    private OffsetDateTime erpPostedAt;
    private Integer erpPaymentId;
    
    @Enumerated(EnumType.STRING)
    private LinkStatus erpSyncedStatus;

    @Enumerated(EnumType.STRING)
    private LinkStatus status;

    private OffsetDateTime expireBy;
    private String gatewayPaymentId;

    @Column(columnDefinition = "text")
    private String errorMessage;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}