package com.santhoshbalaguru.einvoice.client;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

import org.compiere.util.CLogger;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.santhoshbalaguru.einvoice.config.EInvoiceConfig;
import com.santhoshbalaguru.einvoice.model.generateirn.request.InvoiceRequest;

public class EInvoiceApiClient {

	private static final CLogger log = CLogger.getCLogger(EInvoiceApiClient.class);

	private static final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
	private final ObjectMapper objectMapper;

	/** baseUrl: where the API lives (always ends with "/"). ipAddress: only for the ip_address header. */
	private final String userName, password, clientID, clientSecret, gstin, email, baseUrl, ipAddress;

	public EInvoiceApiClient(EInvoiceConfig cfg) {
		this.userName = cfg.userName;
		this.password = cfg.password;
		this.clientID = cfg.clientId;
		this.clientSecret = cfg.clientSecret;
		this.gstin = cfg.gstin;
		this.email = cfg.email;
		this.baseUrl = cfg.baseUrl;
		this.ipAddress = cfg.ipAddress;

		this.objectMapper = new ObjectMapper()
				.setSerializationInclusion(JsonInclude.Include.NON_NULL)
				.enable(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN)   // never 1E+2 style numbers
				.setVisibility(PropertyAccessor.ALL, Visibility.NONE)
				.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
	}

	/** Calls the authenticate endpoint and returns the raw response body. */
	public String authenticate() throws Exception {

		String url = baseUrl + "einvoice/authenticate?email=" + enc(email);
		log.fine("Authenticate: " + url);

		HttpRequest request = HttpRequest.newBuilder()
				.uri(toUri(url))
				.timeout(Duration.ofSeconds(60))
				.header("username", userName)
				.header("password", password)
				.header("ip_address", ipAddress)
				.header("client_id", clientID)
				.header("client_secret", clientSecret)
				.header("gstin", gstin)
				.GET()
				.build();

		return send(request, url);
	}

	/**
	 * Calls the GENERATE endpoint and returns the raw response body.
	 * The caller is responsible for checking status_cd in the response.
	 */
	public String generateIRN(String authToken, InvoiceRequest invoiceRequest) throws Exception {

		String url = baseUrl + "einvoice/type/GENERATE/version/V1_03?email=" + enc(email);

		String jsonBody = objectMapper.writeValueAsString(invoiceRequest);
		int bytes = jsonBody.getBytes(StandardCharsets.UTF_8).length;
		if (bytes > 2 * 1024 * 1024)
		    throw new RuntimeException("The request is " + (bytes / 1024) + " KB, above the IRP limit of 2 MB "
		            + "(2048 KB). Split the invoice into several documents.");
		log.fine("Generate IRN request: " + jsonBody);

		HttpRequest request = HttpRequest.newBuilder()
				.uri(toUri(url))
				.timeout(Duration.ofSeconds(60))
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.header("username", userName)
				.header("ip_address", ipAddress)
				.header("client_id", clientID)
				.header("client_secret", clientSecret)
				.header("gstin", gstin)
				.header("auth-token", authToken)
				.POST(HttpRequest.BodyPublishers.ofString(jsonBody))
				.build();

		return send(request, url);
	}

	/**
	 * Fetches an already-registered IRN by document details (type, number, date).
	 * Used to recover when GENERATE answers "Duplicate IRN" (2150).
	 */
	public String getIrnByDocDetails(String authToken, String docType, String docNo, String docDate)
			throws Exception {

		String url = baseUrl + "einvoice/type/GETIRNBYDOCDETAILS/version/V1_03?param1=" + enc(docNo)
				+ "&email=" + enc(email);

		HttpRequest request = HttpRequest.newBuilder()
				.uri(toUri(url))
				.timeout(Duration.ofSeconds(60))
				.header("Accept", "application/json")
				.header("username", userName)
				.header("ip_address", ipAddress)
				.header("client_id", clientID)
				.header("client_secret", clientSecret)
				.header("gstin", gstin)
				.header("auth-token", authToken)
				.header("doctype", docType)
				.header("docnum", docNo)
				.header("docdate", docDate)
				.GET()
				.build();

		return send(request, url);
	}
	
	
	/** Cancels an IRN. Allowed by the IRP only within 24 hours of generation. */
	public String cancelIRN(String authToken, String irn, String reason, String remarks) throws Exception {

		String url = baseUrl + "einvoice/type/CANCEL/version/V1_03?email=" + enc(email);

		Map<String, String> payload = new LinkedHashMap<>();
		payload.put("Irn", irn);
		payload.put("CnlRsn", reason);
		if (remarks != null && !remarks.isBlank())
			payload.put("CnlRem", remarks);
		String jsonBody = objectMapper.writeValueAsString(payload);
		log.fine("Cancel IRN request: " + jsonBody);

		HttpRequest request = HttpRequest.newBuilder()
				.uri(toUri(url))
				.timeout(Duration.ofSeconds(60))
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.header("username", userName)
				.header("ip_address", ipAddress)
				.header("client_id", clientID)
				.header("client_secret", clientSecret)
				.header("gstin", gstin)
				.header("auth-token", authToken)
				.POST(HttpRequest.BodyPublishers.ofString(jsonBody))
				.build();

		return send(request, url);
	}

	// =====================================================================
	// HELPERS
	// =====================================================================

	/** Sends the request and turns connection problems into readable exceptions. */
	private String send(HttpRequest request, String url) {
		try {
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

			if (log.isLoggable(Level.FINE))
				log.fine("Response [" + response.statusCode() + "] from " + url + ": " + response.body());

			return bodyOrThrow(response);

		} catch (HttpTimeoutException e) {
			throw new RuntimeException("API request timed out: " + url, e);
		} catch (IOException e) {
			throw new RuntimeException("Failed to connect to API: " + url, e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("API request was interrupted", e);
		}
	}

	private static URI toUri(String url) {
		try {
			return new URI(url);
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("Invalid API URL: " + url, e);
		}
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	/**
	 * Returns the body, but fails clearly when the gateway answered with something that is not JSON.
	 * Empty bodies are left to the callers, which already report "Empty response ...".
	 */
	private static String bodyOrThrow(HttpResponse<String> response) {
		String body = response.body();
		boolean empty = body == null || body.isBlank();
		boolean json = !empty && body.trim().startsWith("{");

		if (!json && (response.statusCode() >= 400 || !empty)) {
			String snippet = empty ? "" : body.replaceAll("\\s+", " ").trim();
			throw new RuntimeException("IRP gateway returned HTTP " + response.statusCode() + ": "
					+ (snippet.length() > 200 ? snippet.substring(0, 200) + "..." : snippet));
		}
		return body;
	}
}