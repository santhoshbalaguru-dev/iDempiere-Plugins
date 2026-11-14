package com.santhoshbalaguru.einvoice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.santhoshbalaguru.einvoice.client.EInvoiceApiClient;
import com.santhoshbalaguru.einvoice.config.EInvoiceConfig;

public class EInvoiceCancelIrnService {

	private final EInvoiceApiClient apiClient;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public EInvoiceCancelIrnService(EInvoiceConfig cfg) {
		this.apiClient = new EInvoiceApiClient(cfg);
	}

	public static class CancelResult {
		public String irn;
		public String cancelDate;
		public String rawResponse;
	}

	public CancelResult cancelIrn(String irn, String reason, String remarks, String authToken) throws Exception {
		String body = apiClient.cancelIRN(authToken, irn, reason, remarks);
		if (body == null || body.trim().isEmpty())
			throw new Exception("Empty response received from Cancel IRN API");

		JsonNode root = objectMapper.readTree(body);

		// Accept either "status_cd" or "status" as the success flag
		String status = root.path("status_cd").asText(root.path("status").asText(""));
		if (!"1".equals(status))
			throw new Exception("IRN cancellation failed: " + root.path("status_desc").asText(body));

		JsonNode data = root.path("data");
		if (!data.isObject())
			throw new Exception("IRN cancellation succeeded but response data is missing");

		CancelResult r = new CancelResult();
		r.irn = data.path("Irn").asText(irn);
		r.cancelDate = data.path("CancelDate").asText(null);
		r.rawResponse = body;
		return r;
	}
}