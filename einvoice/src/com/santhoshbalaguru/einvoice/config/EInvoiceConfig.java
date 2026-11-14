package com.santhoshbalaguru.einvoice.config;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MOrgInfo;
import org.compiere.model.PO;
import org.compiere.model.Query;

/**
 * IRP connection settings for one organization (from EI_Config; secrets are decrypted by PO).
 *
 * baseUrl   - where the API lives, e.g. https://apisandbox.whitebooks.in/ (EI_Config.IrpBaseURL)
 * ipAddress - value of the "ip_address" request header (EI_Config.IrpIpAddress, optional)
 */
public final class EInvoiceConfig {

	public final int id;
	public final String userName, password, clientId, clientSecret, email, gstin, baseUrl, ipAddress;

	private EInvoiceConfig(int id, String userName, String password, String clientId, String clientSecret,
			String email, String gstin, String baseUrl, String ipAddress) {
		this.id = id;
		this.userName = userName;
		this.password = password;
		this.clientId = clientId;
		this.clientSecret = clientSecret;
		this.email = email;
		this.gstin = gstin;
		this.baseUrl = baseUrl;
		this.ipAddress = ipAddress;
	}

	/**
	 * Organization-specific row first, else the client-wide row (org 0).
	 * The seller GSTIN comes from the org's Tax ID, the same source the mapper uses.
	 */
	public static EInvoiceConfig load(Properties ctx, int orgId, String trx) {
		PO po = new Query(ctx, "EI_Config", "AD_Org_ID IN (?,0)", trx).setClient_ID().setOnlyActiveRecords(true)
				.setParameters(orgId).setOrderBy("AD_Org_ID DESC").first();
		if (po == null)
			throw new AdempiereException(
					"No E-Invoice Configuration found. Create one in the E-Invoice Configuration window.");

		MOrgInfo oi = MOrgInfo.get(ctx, orgId, trx);
		return build(po.get_ID(), po.get_ValueAsString("IrpUserName"), po.get_ValueAsString("IrpPassword"),
				po.get_ValueAsString("IrpClientID"), po.get_ValueAsString("IrpClientSecret"),
				po.get_ValueAsString("IrpEmail"), oi.getTaxID(), po.get_ValueAsString("IrpBaseURL"),
				po.get_ValueAsString("IrpIpAddress"));
	}

	/** For SampleRequestRunner only (no database): reads EI_* environment variables. */
	public static EInvoiceConfig fromEnvironment() {
		return build(0, System.getenv("EI_USERNAME"), System.getenv("EI_PASSWORD"), System.getenv("EI_CLIENT_ID"),
				System.getenv("EI_CLIENT_SECRET"), System.getenv("EI_EMAIL"), System.getenv("EI_GSTIN"),
				System.getenv("EI_BASE_URL"), System.getenv("EI_IP_ADDRESS"));
	}

	private static EInvoiceConfig build(int id, String userName, String password, String clientId,
			String clientSecret, String email, String gstin, String baseUrl, String ipAddress) {

		List<String> missing = new ArrayList<>();
		if (blank(baseUrl)) missing.add("Base URL");
		if (blank(userName)) missing.add("User Name");
		if (blank(password)) missing.add("Password");
		if (blank(clientId)) missing.add("Client ID");
		if (blank(clientSecret)) missing.add("Client Secret");
		if (blank(email)) missing.add("Email");
		if (blank(gstin)) missing.add("GSTIN (Tax ID on the organization info)");
		if (!missing.isEmpty())
			throw new AdempiereException("E-Invoice configuration incomplete, missing: " + String.join(", ", missing));

		String base = normalizeBaseUrl(baseUrl);

		// The ip_address header: configured value, else EI_IP_ADDRESS, else this machine's address
		String ip = !blank(ipAddress) ? ipAddress : System.getenv("EI_IP_ADDRESS");
		if (blank(ip))
			ip = detectLocalIp();
		if (blank(ip))
			throw new AdempiereException("Could not determine the IP address to send to the IRP. "
					+ "Set IP Address on the E-Invoice Configuration, or the EI_IP_ADDRESS environment variable.");

		return new EInvoiceConfig(id, userName.trim(), password, clientId.trim(), clientSecret, email.trim(),
				gstin.trim(), base, ip.trim());
	}

	/** Must be an absolute http(s) URL; a trailing slash is added so paths can be appended directly. */
	private static String normalizeBaseUrl(String raw) {
		String base = raw.trim();
		URI uri;
		try {
			uri = new URI(base);
		} catch (URISyntaxException e) {
			throw new AdempiereException("IRP Base URL is not a valid URL: " + base);
		}
		String scheme = uri.getScheme();
		if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")))
			throw new AdempiereException("IRP Base URL must start with http:// or https://, got: " + base);
		if (uri.getHost() == null || uri.getHost().isBlank())
			throw new AdempiereException("IRP Base URL has no host: " + base);
		return base.endsWith("/") ? base : base + "/";
	}

	/** Address of the network interface used to reach the internet (a LAN address behind NAT). */
	private static String detectLocalIp() {
		try (DatagramSocket s = new DatagramSocket()) {
			s.connect(InetAddress.getByName("8.8.8.8"), 53);
			String ip = s.getLocalAddress().getHostAddress();
			return "0.0.0.0".equals(ip) ? null : ip;
		} catch (Exception e) {
			return null;
		}
	}

	private static boolean blank(String s) {
		return s == null || s.isBlank();
	}
}