package com.santhoshbalaguru.paymentlink.client;

import java.io.IOException;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.adempiere.exceptions.AdempiereException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.santhoshbalaguru.paymentlink.config.PaymentGatewayConfig;
import com.fasterxml.jackson.databind.DeserializationFeature;

/**
 * Calls the Spring Boot payment service (POST /payment-links) with the X-API-Key from PL_Config.
 * JSON is built and read with Jackson's tree model, and dates travel as ISO-8601 strings.
 */
public final class PaymentServiceClient {

	private static final ObjectMapper MAPPER = new ObjectMapper()
			.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
	
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	/** What iDempiere asks the service for. */
	public static final class LinkRequest {
		public final int adClientId, adOrgId, invoiceId;
		public final String invoiceDocumentNo, currency, customerName, customerEmail, description, gateway;
		public final boolean forceNew;
		public final BigDecimal amount;
		public final OffsetDateTime expireBy;
		

		public LinkRequest(int adClientId, int adOrgId, int invoiceId, String invoiceDocumentNo, BigDecimal amount,
				String currency, String customerName, String customerEmail, OffsetDateTime expireBy,
				String description, String gateway, boolean forceNew) {
			this.adClientId = adClientId;
			this.adOrgId = adOrgId;
			this.invoiceId = invoiceId;
			this.invoiceDocumentNo = invoiceDocumentNo;
			this.amount = amount;
			this.currency = currency;
			this.customerName = customerName;
			this.customerEmail = customerEmail;
			this.expireBy = expireBy;
			this.description = description;
			this.gateway = gateway;
			this.forceNew = forceNew;

		}
	}

	/** What the service answers: serviceLinkNo is the service's own row number for the link. */
	public static final class LinkResponse {
		public final long serviceLinkNo;
		public final String gateway, gatewayLinkRef, shortUrl, status, currency;
		public final BigDecimal amount;
		public final OffsetDateTime expireBy;
		/** true when the service handed back the link it already had (same invoice, same amount) */
		public final boolean reused;

		LinkResponse(long serviceLinkNo, String gateway, String gatewayLinkRef, String shortUrl, String status,
				BigDecimal amount, String currency, OffsetDateTime expireBy, boolean reused) {
			this.serviceLinkNo = serviceLinkNo;
			this.gateway = gateway;
			this.gatewayLinkRef = gatewayLinkRef;
			this.shortUrl = shortUrl;
			this.status = status;
			this.amount = amount;
			this.currency = currency;
			this.expireBy = expireBy;
			this.reused = reused;
		}
	}

	private final PaymentGatewayConfig config;

	public PaymentServiceClient(PaymentGatewayConfig config) {
		this.config = config;
	}

	public LinkResponse createLink(LinkRequest r) {
		ObjectNode body = MAPPER.createObjectNode();
		body.put("adClientId", r.adClientId);
		body.put("adOrgId", r.adOrgId);
		body.put("invoiceId", r.invoiceId);
		body.put("invoiceDocumentNo", r.invoiceDocumentNo);
		body.put("amount", r.amount);
		body.put("currency", r.currency);
		body.put("customerName", r.customerName);
		body.put("customerEmail", r.customerEmail);
		body.put("expireBy", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(r.expireBy));
		body.put("description", r.description);
		body.put("gateway", r.gateway);
		body.put("forceNew", r.forceNew);

		String json;
		try {
			json = MAPPER.writeValueAsString(body);
		} catch (IOException e) {
			throw new AdempiereException("Could not build the payment service request: " + e.getMessage(), e);
		}

		String responseBody = call(HttpRequest.newBuilder(URI.create(config.serviceUrl + "/payment-links"))
				.timeout(Duration.ofSeconds(30))
				.header("Content-Type", "application/json")
				.header("X-API-Key", config.apiKey)
				.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
				.build());

		try {
			JsonNode n = MAPPER.readTree(responseBody);
			OffsetDateTime expireBy = n.hasNonNull("expireBy") ? OffsetDateTime.parse(n.get("expireBy").asText()) : null;
			return new LinkResponse(n.path("id").asLong(), n.path("gateway").asText(null),
					n.path("gatewayLinkId").asText(null), n.path("shortUrl").asText(null),
					n.path("status").asText(null), n.path("amount").decimalValue(), n.path("currency").asText(null),
					expireBy, n.path("reused").asBoolean(false));
		} catch (IOException | RuntimeException e) {
			throw new AdempiereException("Unexpected payment service answer: " + shorten(responseBody), e);
		}
	}

	private static String shorten(String s) {
		if (s == null)
			return "";
		return s.length() > 500 ? s.substring(0, 500) + "..." : s;
	}
	
	/** One link whose status iDempiere has not confirmed yet. */
	public static final class SyncItem {
		public final long id;
		public final int invoiceId;
		public final String gateway, status, currency, gatewayPaymentId, errorMessage;
		public final BigDecimal amount;
		public final OffsetDateTime paidAt;

		SyncItem(long id, int invoiceId, String gateway, String status, BigDecimal amount, String currency,
				String gatewayPaymentId, OffsetDateTime paidAt, String errorMessage) {
			this.id = id;
			this.invoiceId = invoiceId;
			this.gateway = gateway;
			this.status = status;
			this.amount = amount;
			this.currency = currency;
			this.gatewayPaymentId = gatewayPaymentId;
			this.paidAt = paidAt;
			this.errorMessage = errorMessage;
		}
	}

	public List<SyncItem> fetchUnsynced(int adClientId, long afterId, int limit) {
		HttpRequest request = HttpRequest
				.newBuilder(URI.create(config.serviceUrl + "/payment-links/unsynced?adClientId=" + adClientId
						+ "&afterId=" + afterId + "&limit=" + limit))
				.timeout(Duration.ofSeconds(30)).header("X-API-Key", config.apiKey).GET().build();
		String body = call(request);
		try {
			List<SyncItem> items = new ArrayList<>();
			for (JsonNode n : MAPPER.readTree(body)) {
				OffsetDateTime paidAt = n.hasNonNull("paidAt") ? OffsetDateTime.parse(n.get("paidAt").asText()) : null;
				items.add(new SyncItem(n.path("id").asLong(), n.path("invoiceId").asInt(),
						n.path("gateway").asText(null), n.path("status").asText(null),
						n.path("amount").decimalValue(), n.path("currency").asText(null),
						n.path("gatewayPaymentId").asText(null), paidAt, n.path("errorMessage").asText(null)));
			}
			return items;
		} catch (IOException | RuntimeException e) {
			throw new AdempiereException("Unexpected payment service answer: " + shorten(body), e);
		}
	}

	/** Tells the service iDempiere has applied this status (and the payment, if one was created). */
	public void markSynced(long serviceLinkNo, String status, Integer erpPaymentId) {
		ObjectNode body = MAPPER.createObjectNode();
		body.put("status", status);
		if (erpPaymentId != null)
			body.put("erpPaymentId", erpPaymentId);
		String json;
		try {
			json = MAPPER.writeValueAsString(body);
		} catch (IOException e) {
			throw new AdempiereException("Could not build the payment service request: " + e.getMessage(), e);
		}
		call(HttpRequest.newBuilder(URI.create(config.serviceUrl + "/payment-links/" + serviceLinkNo + "/synced"))
				.timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json")
				.header("X-API-Key", config.apiKey)
				.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build());
	}

	private String call(HttpRequest request) {
		HttpResponse<String> response;
		try {
			response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AdempiereException("Interrupted while calling the payment service", e);
		} catch (IOException e) {
			throw new AdempiereException("Payment service not reachable at " + config.serviceUrl + ": " + e.getMessage(), e);
		}
		if (response.statusCode() != 200)
			throw new AdempiereException(
					"Payment service answered HTTP " + response.statusCode() + ": " + shorten(response.body()));
		return response.body();
	}
}
