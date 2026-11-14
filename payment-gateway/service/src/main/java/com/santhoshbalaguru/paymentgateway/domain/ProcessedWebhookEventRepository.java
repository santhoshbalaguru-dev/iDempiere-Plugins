package com.santhoshbalaguru.paymentgateway.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedWebhookEventRepository
        extends JpaRepository<ProcessedWebhookEvent, Long> {

    /** Returns 1 if this event is new, 0 if it was already recorded. Atomic, so safe under retries. */
    @Modifying
    @Query(value = """
            INSERT INTO processed_webhook_event (gateway, event_id, event_type)
            VALUES (:gateway, :eventId, :eventType)
            ON CONFLICT (gateway, event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("gateway") String gateway,
                       @Param("eventId") String eventId,
                       @Param("eventType") String eventType);
}