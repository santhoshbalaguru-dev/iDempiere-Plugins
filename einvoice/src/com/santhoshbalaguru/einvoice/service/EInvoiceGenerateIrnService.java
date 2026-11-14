package com.santhoshbalaguru.einvoice.service;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.santhoshbalaguru.einvoice.client.EInvoiceApiClient;
import com.santhoshbalaguru.einvoice.config.EInvoiceConfig;
import com.santhoshbalaguru.einvoice.mapper.EInvoiceRequestMapper;
import com.santhoshbalaguru.einvoice.model.MSBInvoice;
import com.santhoshbalaguru.einvoice.model.generateirn.request.InvoiceRequest;
import com.santhoshbalaguru.einvoice.model.generateirn.request.IrpSchemaValidator;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;

public class EInvoiceGenerateIrnService {

	private final EInvoiceApiClient apiClient;
	private final EInvoiceRequestMapper requestMapper;
	private final ObjectMapper objectMapper;

	public EInvoiceGenerateIrnService(EInvoiceConfig cfg) {
		this(new EInvoiceApiClient(cfg), new EInvoiceRequestMapper());
	}

	public EInvoiceGenerateIrnService(EInvoiceApiClient apiClient, EInvoiceRequestMapper requestMapper) {
		this.apiClient = apiClient;
		this.requestMapper = requestMapper;
		this.objectMapper = new ObjectMapper();
	}

	/** Values returned by the IRP after a successful IRN generation. */
	public static class IrnResult {
		public String irn;
		public String ackNo;
		public String ackDt;
		public String signedInvoice;
		public String signedQrCode;
		public String status;
		public String ewbNo;
		public String ewbDt;
		public String ewbValidTill;
		public String rawResponse;
		public boolean recovered;

	}

	/**
	 * Builds the request from the invoice, calls the IRP and returns the parsed result.
	 *
	 * @throws Exception if the API call fails or the IRP rejects the invoice
	 */
	public IrnResult generateIrn(MSBInvoice invoice, String authToken) throws Exception {

		InvoiceRequest request = requestMapper.mapInvoiceRequest(invoice);

		List<String> problems = IrpSchemaValidator.validate(request);
		if (!problems.isEmpty())
			throw new Exception("Invoice does not meet the IRP schema:\n - " + String.join("\n - ", problems));

		String body = apiClient.generateIRN(authToken, request);

		if (body == null || body.trim().isEmpty())
			throw new Exception("Empty response received from Generate IRN API");

		JsonNode root = objectMapper.readTree(body);
		boolean recovered = false;

		if (!"1".equals(root.path("status_cd").asText())) {
			if (!isDuplicateIrn(root))
				throw new Exception("IRN generation failed: " + root.path("status_desc").asText(body));

			// The IRP already holds this document (earlier save failed, or a retry). Fetch its IRN.
			body = recoverDuplicate(request, authToken);
			root = objectMapper.readTree(body);
			recovered = true;
		}

		return parseSuccess(root, body, recovered);
	}

	/** Error 2150 / "Duplicate IRN". Checks only the status text and error node, not the whole body. */
	private static boolean isDuplicateIrn(JsonNode root) {
		String text = (root.path("status_desc").asText("") + " " + root.path("error").toString()).toLowerCase();
		return text.contains("2150") || text.contains("duplicate irn");
	}

	private String recoverDuplicate(InvoiceRequest request, String authToken) throws Exception {
	    String body = apiClient.getIrnByDocDetails(authToken, request.getDocDtls().getTyp(),
	            request.getDocDtls().getNo(), request.getDocDtls().getDt());

	    if (body == null || body.trim().isEmpty())
	        throw new Exception("IRP reported a duplicate IRN, but the lookup by document details returned nothing");

	    JsonNode root = objectMapper.readTree(body);
	    if (!"1".equals(root.path("status_cd").asText()))
	        throw new Exception("IRP reported a duplicate IRN, but the lookup by document details failed: "
	                + root.path("status_desc").asText(body)
	                + " (the IRP only returns an IRN by document details within 3 days of generation)");

	    verifyRecovered(root.path("data"), request);
	    return body;
	}
	
	/** Makes sure the IRN found by document details really belongs to the invoice we just tried to register. */
	private void verifyRecovered(JsonNode data, InvoiceRequest request) throws Exception {
	    String docNo = request.getDocDtls().getNo();

	    if ("CNL".equalsIgnoreCase(textOrNull(data, "Status")))
	        throw new Exception("Document number " + docNo + " was already registered at the IRP and that IRN "
	                + "is cancelled. A cancelled IRN cannot be regenerated, so this document needs a new number.");

	    String jwt = textOrNull(data, "SignedInvoice");
	    if (jwt == null)
	        throw new Exception("IRP reported a duplicate IRN for " + docNo + ", but the lookup returned no "
	                + "SignedInvoice, so it cannot be checked against this invoice. Not attaching it.");

	    JsonNode inv = decodeSignedInvoice(jwt);

	    List<String> diffs = new ArrayList<>();
	    same(diffs, "document type", request.getDocDtls().getTyp(), inv.path("DocDtls").path("Typ").asText(null));
	    same(diffs, "document number", docNo, inv.path("DocDtls").path("No").asText(null));
	    same(diffs, "document date", request.getDocDtls().getDt(), inv.path("DocDtls").path("Dt").asText(null));
	    same(diffs, "seller GSTIN", request.getSellerDtls().getGstin(), inv.path("SellerDtls").path("Gstin").asText(null));
	    same(diffs, "buyer GSTIN", request.getBuyerDtls().getGstin(), inv.path("BuyerDtls").path("Gstin").asText(null));

	    JsonNode tot = inv.path("ValDtls").path("TotInvVal");
	    BigDecimal sent = request.getValDtls().getTotInvVal();
	    if (!tot.isNumber() || tot.decimalValue().compareTo(sent) != 0)
	        diffs.add("invoice total: this invoice " + sent.toPlainString() + ", registered "
	                + (tot.isNumber() ? tot.decimalValue().toPlainString() : "missing"));

	    if (!diffs.isEmpty())
	        throw new Exception("The IRP already holds a different document under number " + docNo
	                + " for this financial year (" + String.join("; ", diffs) + "). Use a unique number per "
	                + "document type and financial year.");
	}

	/** Reads the invoice out of the JWT payload. The signature is not verified; this is only a content check. */
	private JsonNode decodeSignedInvoice(String jwt) throws Exception {
	    String[] parts = jwt.split("\\.");
	    if (parts.length < 2)
	        throw new Exception("SignedInvoice returned by the IRP is not a valid JWT");
	    byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
	    JsonNode claims = objectMapper.readTree(new String(payload, StandardCharsets.UTF_8));

	    // The registered invoice sits in the "data" claim, normally as a JSON string
	    JsonNode inv = claims.path("data");
	    if (inv.isTextual())
	        inv = objectMapper.readTree(inv.asText());
	    if (!inv.isObject())
	        throw new Exception("SignedInvoice payload has no invoice data");
	    return inv;
	}

	private static void same(List<String> diffs, String what, String mine, String theirs) {
	    if (mine == null ? theirs != null : !mine.equalsIgnoreCase(theirs == null ? "" : theirs))
	        diffs.add(what + ": this invoice " + mine + ", registered " + theirs);
	}

	private IrnResult parseSuccess(JsonNode root, String body, boolean recovered) throws Exception {
		JsonNode data = root.path("data");
		if (data.isMissingNode() || data.isNull() || !data.isObject())
			throw new Exception("IRN generation succeeded but response data is missing");

		IrnResult result = new IrnResult();
		result.irn = textOrNull(data, "Irn");
		result.ackNo = textOrNull(data, "AckNo");
		result.ackDt = textOrNull(data, "AckDt");
		result.signedInvoice = textOrNull(data, "SignedInvoice");
		result.signedQrCode = textOrNull(data, "SignedQRCode");
		result.status = textOrNull(data, "Status");
		result.ewbNo = textOrNull(data, "EwbNo");
		result.ewbDt = textOrNull(data, "EwbDt");
		result.ewbValidTill = textOrNull(data, "EwbValidTill");
		result.rawResponse = body;
		result.recovered = recovered;

		if (result.irn == null)
			throw new Exception("IRN generation succeeded but Irn is missing in the response");
		return result;
	}

	private static String textOrNull(JsonNode node, String field) {
		JsonNode v = node.get(field);
		return (v == null || v.isNull()) ? null : v.asText();
	}
}