package com.santhoshbalaguru.paymentgateway.service;

import com.santhoshbalaguru.paymentgateway.api.CreatePaymentLinkRequest;
import com.santhoshbalaguru.paymentgateway.domain.LinkStatus;
import com.santhoshbalaguru.paymentgateway.domain.PaymentLink;
import com.santhoshbalaguru.paymentgateway.domain.PaymentLinkRepository;
import com.santhoshbalaguru.paymentgateway.gateway.GatewayLink;
import com.santhoshbalaguru.paymentgateway.gateway.GatewayRegistry;
import com.santhoshbalaguru.paymentgateway.gateway.PaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.santhoshbalaguru.paymentgateway.gateway.GatewayException;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentLinkServiceTest {

	private static final int INVOICE = 1002;

	@Mock PaymentLinkRepository repo;
	@Mock GatewayRegistry gateways;
	@Mock PaymentGateway gateway;

	private PaymentLinkService service;

	@BeforeEach
	void setUp() {
		service = new PaymentLinkService(repo, gateways);
		when(gateways.get(any())).thenReturn(gateway);
	}

	private static CreatePaymentLinkRequest request(Boolean forceNew) {
		return new CreatePaymentLinkRequest(11, 11, INVOICE, "INV-1003", new BigDecimal("1500.50"), "INR",
				"Test Customer", "test@example.com", null, OffsetDateTime.now().plusDays(5), "Invoice INV-1003",
				null, forceNew);
	}

	private static PaymentLink mismatch(LinkStatus syncedStatus) {
		PaymentLink l = new PaymentLink();
		l.setStatus(LinkStatus.AMOUNT_MISMATCH);
		l.setErpSyncedStatus(syncedStatus);
		l.setErrorMessage("Paid 100 INR, expected 150050 INR");
		return l;
	}

	private void stubSuccessfulCreate() {
		when(gateway.code()).thenReturn("razorpay");
		when(repo.saveAndFlush(any(PaymentLink.class))).thenAnswer(i -> i.getArgument(0));
		when(repo.save(any(PaymentLink.class))).thenAnswer(i -> i.getArgument(0));
		when(gateway.createLink(any())).thenReturn(new GatewayLink("plink_new", "https://rzp.io/x"));
	}

    

	@Test
	void paidAndNotYetPostedBlocksNewLinks() {
		when(repo.existsByInvoiceIdAndStatusInAndErpPostedAtIsNull(INVOICE, List.of(LinkStatus.PAID)))
				.thenReturn(true);

		ConflictException e = assertThrows(ConflictException.class, () -> service.createOrReuse(request(true)));
		assertTrue(e.getMessage().contains("waiting to be posted"));
	}

	@Test
	void mismatchNotYetRecordedInIdempiereBlocksEvenRegenerate() {
		when(repo.findFirstByInvoiceIdAndStatusNotOrderByIdDesc(INVOICE, LinkStatus.FAILED))
				.thenReturn(Optional.of(mismatch(null)));

		ConflictException e = assertThrows(ConflictException.class, () -> service.createOrReuse(request(true)));
		assertTrue(e.getMessage().contains("not recorded it yet"));
	}

	@Test
	void recordedMismatchNeedsRegenerate() {
		when(repo.findFirstByInvoiceIdAndStatusNotOrderByIdDesc(INVOICE, LinkStatus.FAILED))
				.thenReturn(Optional.of(mismatch(LinkStatus.AMOUNT_MISMATCH)));

		ConflictException e = assertThrows(ConflictException.class, () -> service.createOrReuse(request(null)));
		assertTrue(e.getMessage().contains("use Regenerate"));
	}

	@Test
	void recordedMismatchAllowsRegenerate() {
		when(repo.findFirstByInvoiceIdAndStatusNotOrderByIdDesc(INVOICE, LinkStatus.FAILED))
				.thenReturn(Optional.of(mismatch(LinkStatus.AMOUNT_MISMATCH)));
		stubSuccessfulCreate();

		PaymentLinkService.Result result = service.createOrReuse(request(true));

		assertFalse(result.reused());
		assertEquals(LinkStatus.CREATED, result.link().getStatus());
		assertEquals("plink_new", result.link().getGatewayLinkId());
	}

	@Test
	void sameAmountReusesTheActiveLink() {
		PaymentLink active = new PaymentLink();
		active.setGateway("razorpay");
		active.setStatus(LinkStatus.CREATED);
		active.setAmountPaise(150050);
		active.setExpireBy(OffsetDateTime.now().plusDays(3));
		when(gateway.code()).thenReturn("razorpay");
		when(repo.findByInvoiceIdAndStatus(INVOICE, LinkStatus.CREATED)).thenReturn(Optional.of(active));
        when(repo.findByInvoiceIdAndStatus(INVOICE, LinkStatus.CREATING)).thenReturn(Optional.empty());

		PaymentLinkService.Result result = service.createOrReuse(request(null));

		assertTrue(result.reused());
		assertEquals(active, result.link());
	}

	@Test
	void amountWithTooManyDecimalsIsRejected() {
		CreatePaymentLinkRequest bad = new CreatePaymentLinkRequest(11, 11, INVOICE, "INV-1003",
				new BigDecimal("10.555"), "INR", "Test Customer", "test@example.com", null,
				OffsetDateTime.now().plusDays(5), null, null, null);
		assertThrows(IllegalArgumentException.class, () -> service.createOrReuse(bad));
	}

		private static PaymentLink activeLink(long amountPaise, OffsetDateTime expireBy) {
		PaymentLink l = new PaymentLink();
		l.setGateway("razorpay");
		l.setGatewayLinkId("plink_old");
		l.setStatus(LinkStatus.CREATED);
		l.setAmountPaise(amountPaise);
		l.setExpireBy(expireBy);
		return l;
	}

		/** The service looks for a stuck CREATING row first, then for the active CREATED one. */
	private void stubActiveLink(PaymentLink old) {
		when(repo.findByInvoiceIdAndStatus(INVOICE, LinkStatus.CREATING)).thenReturn(Optional.empty());
		when(repo.findByInvoiceIdAndStatus(INVOICE, LinkStatus.CREATED)).thenReturn(Optional.of(old));
	}

	@Test
	void changedAmountCancelsTheOldLinkAndCreatesANewOne() {
		PaymentLink old = activeLink(100000, OffsetDateTime.now().plusDays(3));
		stubActiveLink(old);
		stubSuccessfulCreate();

		PaymentLinkService.Result result = service.createOrReuse(request(null));

		verify(gateway).cancelLink("plink_old");
		assertEquals(LinkStatus.CANCELLED, old.getStatus());
		assertFalse(result.reused());
		assertEquals("plink_new", result.link().getGatewayLinkId());
	}

	@Test
	void expiredLinkIsReplacedWithoutCallingCancel() {
		PaymentLink old = activeLink(150050, OffsetDateTime.now().minusHours(1));
		stubActiveLink(old);
		stubSuccessfulCreate();

		service.createOrReuse(request(null));

		verify(gateway, never()).cancelLink(any());
		assertEquals(LinkStatus.EXPIRED, old.getStatus());
	}

	@Test
	void cancelFailureLeavesTheOldLinkUntouched() {
		PaymentLink old = activeLink(100000, OffsetDateTime.now().plusDays(3));
		stubActiveLink(old);
		when(gateway.code()).thenReturn("razorpay");
		doThrow(new GatewayException("boom", null)).when(gateway).cancelLink("plink_old");

		assertThrows(GatewayException.class, () -> service.createOrReuse(request(null)));

		assertEquals(LinkStatus.CREATED, old.getStatus());
		verify(repo, never()).saveAndFlush(any());
	}

	@Test
	void concurrentCreateLosesWithAConflictAndNeverCallsTheGateway() {
		when(gateway.code()).thenReturn("razorpay");
		when(repo.saveAndFlush(any(PaymentLink.class))).thenThrow(new DataIntegrityViolationException("dup"));

		assertThrows(ConflictException.class, () -> service.createOrReuse(request(null)));

		verify(gateway, never()).createLink(any());
	}

	@Test
	void gatewayFailureMarksTheNewRowFailed() {
		when(gateway.code()).thenReturn("razorpay");
		when(repo.saveAndFlush(any(PaymentLink.class))).thenAnswer(i -> i.getArgument(0));
		when(repo.save(any(PaymentLink.class))).thenAnswer(i -> i.getArgument(0));
		when(gateway.createLink(any())).thenThrow(new GatewayException("boom", null));

		assertThrows(GatewayException.class, () -> service.createOrReuse(request(null)));

		ArgumentCaptor<PaymentLink> saved = ArgumentCaptor.forClass(PaymentLink.class);
		verify(repo).save(saved.capture());
		assertEquals(LinkStatus.FAILED, saved.getValue().getStatus());
		assertEquals("boom", saved.getValue().getErrorMessage());
	}
}