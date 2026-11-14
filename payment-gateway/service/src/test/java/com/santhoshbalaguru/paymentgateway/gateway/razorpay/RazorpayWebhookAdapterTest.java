package com.santhoshbalaguru.paymentgateway.gateway.razorpay;

import com.santhoshbalaguru.paymentgateway.config.RazorpayProperties;
import com.santhoshbalaguru.paymentgateway.gateway.InvalidSignatureException;
import com.santhoshbalaguru.paymentgateway.gateway.PaymentEvent;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RazorpayWebhookAdapterTest {

	private static final String SECRET = "test-secret";

	private static final String PAID = """
			{"event":"payment_link.paid","payload":{
			  "payment_link":{"entity":{"id":"plink_1"}},
			  "payment":{"entity":{"id":"pay_1","amount":150050,"currency":"INR"}}}}""";

	private final RazorpayWebhookAdapter adapter =
			new RazorpayWebhookAdapter(new RazorpayProperties("key", "secret", SECRET));

	private static String sign(String secret, byte[] body) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return HexFormat.of().formatHex(mac.doFinal(body));
	}

	private static Map<String, String> headers(String signature) {
		return Map.of("x-razorpay-signature", signature, "x-razorpay-event-id", "evt_1");
	}

	@Test
	void paidEventIsParsed() throws Exception {
		byte[] body = PAID.getBytes(StandardCharsets.UTF_8);
		PaymentEvent ev = adapter.verifyAndParse(body, headers(sign(SECRET, body)));

		assertEquals(PaymentEvent.Type.PAID, ev.type());
		assertEquals("evt_1", ev.eventId());
		assertEquals("plink_1", ev.gatewayLinkId());
		assertEquals("pay_1", ev.gatewayPaymentId());
		assertEquals(150050L, ev.amountPaidMinor());
		assertEquals("INR", ev.currency());
	}

	@Test
	void expiredEventNeedsNoPayment() throws Exception {
		byte[] body = """
				{"event":"payment_link.expired","payload":{"payment_link":{"entity":{"id":"plink_2"}}}}"""
				.getBytes(StandardCharsets.UTF_8);
		PaymentEvent ev = adapter.verifyAndParse(body, headers(sign(SECRET, body)));

		assertEquals(PaymentEvent.Type.EXPIRED, ev.type());
		assertEquals("plink_2", ev.gatewayLinkId());
	}

	@Test
	void unknownEventIsIgnoredButStillVerified() throws Exception {
		byte[] body = "{\"event\":\"order.paid\"}".getBytes(StandardCharsets.UTF_8);
		PaymentEvent ev = adapter.verifyAndParse(body, headers(sign(SECRET, body)));
		assertEquals(PaymentEvent.Type.IGNORED, ev.type());

		assertThrows(InvalidSignatureException.class, () -> adapter.verifyAndParse(body, headers("00")));
	}

	@Test
	void wrongSignatureIsRejected() throws Exception {
		byte[] body = PAID.getBytes(StandardCharsets.UTF_8);
		assertThrows(InvalidSignatureException.class,
				() -> adapter.verifyAndParse(body, headers(sign("other-secret", body))));
	}

	@Test
	void signatureOverTamperedBodyIsRejected() throws Exception {
		byte[] body = PAID.getBytes(StandardCharsets.UTF_8);
		String signature = sign(SECRET, body);
		byte[] tampered = PAID.replace("150050", "100").getBytes(StandardCharsets.UTF_8);
		assertThrows(InvalidSignatureException.class, () -> adapter.verifyAndParse(tampered, headers(signature)));
	}

	@Test
	void missingSignatureIsRejected() {
		byte[] body = PAID.getBytes(StandardCharsets.UTF_8);
		assertThrows(InvalidSignatureException.class, () -> adapter.verifyAndParse(body, Map.of()));
	}

	@Test
	void unconfiguredSecretRejectsEverything() {
		byte[] body = PAID.getBytes(StandardCharsets.UTF_8);
		for (String secret : new String[] { "", "   ", null }) {
			RazorpayWebhookAdapter noSecret =
					new RazorpayWebhookAdapter(new RazorpayProperties("key", "secret", secret));
			assertThrows(InvalidSignatureException.class,
					() -> noSecret.verifyAndParse(body, headers("deadbeef")));
		}
	}

	@Test
	void missingEventIdFallsBackToBodyHash() throws Exception {
		byte[] body = PAID.getBytes(StandardCharsets.UTF_8);
		PaymentEvent ev = adapter.verifyAndParse(body, Map.of("x-razorpay-signature", sign(SECRET, body)));
		assertTrue(ev.eventId().startsWith("sha256-"));
	}

	@Test
	void malformedPayloadIsABadRequest() throws Exception {
		byte[] body = "{\"event\":\"payment_link.paid\",\"payload\":{}}".getBytes(StandardCharsets.UTF_8);
		assertThrows(IllegalArgumentException.class,
				() -> adapter.verifyAndParse(body, headers(sign(SECRET, body))));
	}
}