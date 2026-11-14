package com.santhoshbalaguru.paymentlink.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.PO;
import org.compiere.model.Query;

/**
 * Payment service settings for one organization (from PL_Config; the API key is decrypted by PO).
 *
 * serviceUrl - base URL of the Spring Boot payment service, no trailing slash
 * apiKey     - value of the X-API-Key header
 */
public final class PaymentGatewayConfig {

	public final int id;
	public final String gateway, serviceUrl, apiKey;
	public final int bankAccountId, mailTextId, expiryDays;

	private PaymentGatewayConfig(int id, String gateway, String serviceUrl, String apiKey,
			int bankAccountId, int mailTextId, int expiryDays) {
		this.id = id;
		this.gateway = gateway;
		this.serviceUrl = serviceUrl;
		this.apiKey = apiKey;
		this.bankAccountId = bankAccountId;
		this.mailTextId = mailTextId;
		this.expiryDays = expiryDays;
	}
	
	public static PaymentGatewayConfig load(Properties ctx, int orgId, String trx) {
		PO po = new Query(ctx, "pl_config", "AD_Org_ID IN (?,0)", trx).setClient_ID().setOnlyActiveRecords(true)
				.setParameters(orgId).setOrderBy("AD_Org_ID DESC").first();
		if (po == null)
			throw new AdempiereException(
					"No Payment Gateway Configuration found. Create one in the Payment Gateway Configuration window.");
		return from(po);
	}

	/** All active configurations of the client. */
	public static List<PaymentGatewayConfig> loadAll(Properties ctx, String trx) {
		List<PaymentGatewayConfig> result = new ArrayList<>();
		for (PO po : new Query(ctx, "pl_config", "1=1", trx).setClient_ID().setOnlyActiveRecords(true)
				.setOrderBy("AD_Org_ID").list())
			result.add(from(po));
		return result;
	}

	private static PaymentGatewayConfig from(PO po) {
		List<String> missing = new ArrayList<>();
		String gateway = po.get_ValueAsString("Gateway");
		String serviceUrl = po.get_ValueAsString("ServiceUrl");
		String apiKey = po.get_ValueAsString("ServiceApiKey");
		int expiryDays = po.get_ValueAsInt("ExpiryDays");
		if (blank(gateway)) missing.add("Gateway");
		if (blank(serviceUrl)) missing.add("Service URL");
		if (blank(apiKey)) missing.add("Service API Key");
		if (expiryDays <= 0) missing.add("Expiry Days (must be 1 or more)");
		if (!missing.isEmpty())
			throw new AdempiereException(
					"Payment Gateway configuration incomplete, missing: " + String.join(", ", missing));

		return new PaymentGatewayConfig(po.get_ID(), gateway.trim().toLowerCase(), normalizeUrl(serviceUrl),
				apiKey.trim(), po.get_ValueAsInt("C_BankAccount_ID"), po.get_ValueAsInt("R_MailText_ID"),
				expiryDays);
	}

	/** Absolute http(s) URL; http only for this machine. Trailing slash removed so "/payment-links" can be appended. */
	private static String normalizeUrl(String raw) {
		String base = raw.trim();
		URI uri;
		try {
			uri = new URI(base);
		} catch (URISyntaxException e) {
			throw new AdempiereException("Service URL is not a valid URL: " + base);
		}
		String scheme = uri.getScheme();
		if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")))
			throw new AdempiereException("Service URL must start with http:// or https://, got: " + base);
		String host = uri.getHost();
		if (host == null || host.isBlank())
			throw new AdempiereException("Service URL has no host: " + base);
		boolean local = host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("[::1]");
		if (!local && !scheme.equalsIgnoreCase("https"))
			throw new AdempiereException("Service URL must use https unless it points to this machine "
					+ "(the API key is sent with every call), got: " + base);
		return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
	}

	private static boolean blank(String s) {
		return s == null || s.isBlank();
	}
}