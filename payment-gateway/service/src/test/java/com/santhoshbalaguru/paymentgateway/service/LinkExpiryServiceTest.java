package com.santhoshbalaguru.paymentgateway.service;

import com.santhoshbalaguru.paymentgateway.domain.LinkStatus;
import com.santhoshbalaguru.paymentgateway.domain.PaymentLinkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkExpiryServiceTest {

	@Mock PaymentLinkRepository repo;

	@Test
	void expiresOnlyLinksOlderThanTheGracePeriod() {
		OffsetDateTime now = OffsetDateTime.now();
		when(repo.expireOverdue(LinkStatus.CREATED, LinkStatus.EXPIRED, now, now.minus(LinkExpiryService.GRACE)))
				.thenReturn(2);

		assertEquals(2, new LinkExpiryService(repo).expireOverdue(now));
	}
}