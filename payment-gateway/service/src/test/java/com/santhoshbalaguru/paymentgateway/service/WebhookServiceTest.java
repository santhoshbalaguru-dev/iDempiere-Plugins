package com.santhoshbalaguru.paymentgateway.service;

import com.santhoshbalaguru.paymentgateway.domain.LinkStatus;
import com.santhoshbalaguru.paymentgateway.domain.PaymentLink;
import com.santhoshbalaguru.paymentgateway.domain.PaymentLinkRepository;
import com.santhoshbalaguru.paymentgateway.domain.ProcessedWebhookEventRepository;
import com.santhoshbalaguru.paymentgateway.gateway.PaymentEvent;
import com.santhoshbalaguru.paymentgateway.gateway.WebhookAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

	@Mock WebhookAdapter adapter;
	@Mock ProcessedWebhookEventRepository events;
	@Mock PaymentLinkRepository links;

	private WebhookService service;

	@BeforeEach
	void setUp() {
		when(adapter.code()).thenReturn("razorpay");
		service = new WebhookService(List.of(adapter), events, links);
	}

	private static PaymentLink link(LinkStatus status) {
		PaymentLink l = new PaymentLink();
		l.setGateway("razorpay");
		l.setGatewayLinkId("plink_1");
		l.setStatus(status);
		l.setAmountPaise(150050);
		l.setCurrency("INR");
		return l;
	}

	private void event(PaymentEvent.Type type, long paid) {
		when(adapter.verifyAndParse(any(), any())).thenReturn(
				new PaymentEvent("evt_1", "payment_link.paid", type, "plink_1", "pay_1", paid, "INR"));
	}

	private WebhookService.Outcome handle() {
		return service.handle("razorpay", new byte[0], Map.of());
	}

	@Test
	void matchingPaymentMarksLinkPaid() {
		PaymentLink l = link(LinkStatus.CREATED);
		event(PaymentEvent.Type.PAID, 150050);
		when(events.insertIfAbsent("razorpay", "evt_1", "payment_link.paid")).thenReturn(1);
		when(links.findByGatewayAndGatewayLinkId("razorpay", "plink_1")).thenReturn(Optional.of(l));

		assertEquals(WebhookService.Outcome.PROCESSED, handle());
		assertEquals(LinkStatus.PAID, l.getStatus());
		assertEquals("pay_1", l.getGatewayPaymentId());
		assertNotNull(l.getPaidAt());
	}

	@Test
	void differentAmountMarksMismatch() {
		PaymentLink l = link(LinkStatus.CREATED);
		event(PaymentEvent.Type.PAID, 100);
		when(events.insertIfAbsent("razorpay", "evt_1", "payment_link.paid")).thenReturn(1);
		when(links.findByGatewayAndGatewayLinkId("razorpay", "plink_1")).thenReturn(Optional.of(l));

		handle();

		assertEquals(LinkStatus.AMOUNT_MISMATCH, l.getStatus());
		assertEquals("Paid 100 INR, expected 150050 INR", l.getErrorMessage());
	}

	@Test
	void paymentOnAnExpiredLinkIsPaidButFlaggedForReview() {
		PaymentLink l = link(LinkStatus.EXPIRED);
		event(PaymentEvent.Type.PAID, 150050);
		when(events.insertIfAbsent("razorpay", "evt_1", "payment_link.paid")).thenReturn(1);
		when(links.findByGatewayAndGatewayLinkId("razorpay", "plink_1")).thenReturn(Optional.of(l));

		handle();

		assertEquals(LinkStatus.PAID, l.getStatus());
		assertEquals("Payment received while link was EXPIRED; review needed", l.getErrorMessage());
	}

	@Test
	void duplicateEventIsSkippedWithoutTouchingTheLink() {
		event(PaymentEvent.Type.PAID, 150050);
		when(events.insertIfAbsent("razorpay", "evt_1", "payment_link.paid")).thenReturn(0);

		assertEquals(WebhookService.Outcome.DUPLICATE, handle());
		verifyNoInteractions(links);
	}

	@Test
	void expiredEventOnlyAffectsAnActiveLink() {
		PaymentLink paid = link(LinkStatus.PAID);
		event(PaymentEvent.Type.EXPIRED, 0);
		when(events.insertIfAbsent("razorpay", "evt_1", "payment_link.paid")).thenReturn(1);
		when(links.findByGatewayAndGatewayLinkId("razorpay", "plink_1")).thenReturn(Optional.of(paid));

		handle();

		assertEquals(LinkStatus.PAID, paid.getStatus());
	}

		@Test
	void paidEventForUnknownLinkFailsSoTheGatewayRetries() {
		event(PaymentEvent.Type.PAID, 150050);
		when(events.insertIfAbsent("razorpay", "evt_1", "payment_link.paid")).thenReturn(1);
		when(links.findByGatewayAndGatewayLinkId("razorpay", "plink_1")).thenReturn(Optional.empty());

		assertThrows(UnknownLinkException.class, this::handle);
	}

	@Test
	void expiredEventForUnknownLinkIsIgnored() {
		event(PaymentEvent.Type.EXPIRED, 0);
		when(events.insertIfAbsent("razorpay", "evt_1", "payment_link.paid")).thenReturn(1);
		when(links.findByGatewayAndGatewayLinkId("razorpay", "plink_1")).thenReturn(Optional.empty());

		assertEquals(WebhookService.Outcome.IGNORED, handle());
	}
}