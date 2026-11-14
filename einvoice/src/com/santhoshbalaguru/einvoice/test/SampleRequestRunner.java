package com.santhoshbalaguru.einvoice.test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.santhoshbalaguru.einvoice.client.EInvoiceApiClient;
import com.santhoshbalaguru.einvoice.config.EInvoiceConfig;
import com.santhoshbalaguru.einvoice.model.generateirn.request.InvoiceRequest;
import com.santhoshbalaguru.einvoice.model.generateirn.request.IrpSchemaValidator;
import com.santhoshbalaguru.einvoice.service.EInvoiceAuthService;

/**
 * Test harness for the IRP sample request. Does not touch the iDempiere database.
 *
 * <pre>
 *   (no args)         load sample-request.json, check it against the models and the IRP schema, print the payload
 *   send              same, then authenticate and call GENERATE on the Whitebooks sandbox
 *   send --keep-docno keep DocDtls.No from the file (the IRP rejects a document number it has already accepted)
 *   &lt;path&gt;           use your own JSON file instead of the bundled sample
 * </pre>
 * Run it as a plain Java application from the plugin project (send mode needs the plugin classpath).
 * Delete this class before packaging if you do not want it in the bundle.
 */
public class SampleRequestRunner {
	

	public static void main(String[] args) throws Exception {
		boolean send = false, keepDocNo = false;
		String file = null;
		for (String a : args) {
			if ("send".equals(a))
				send = true;
			else if ("--keep-docno".equals(a))
				keepDocNo = true;
			else
				file = a;
		}

		String json = file != null ? Files.readString(Path.of(file)) : readResource("/sample-request.json");

		// The IRP sample uses a few keys in different case (Accdet, Fininsbr, Tendrefr, Transid ...)
		@SuppressWarnings("deprecation") // configure(MapperFeature, boolean) is deprecated but is the exported API; JsonMapper.builder() is not
		ObjectMapper reader = new ObjectMapper().configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);

		// Same settings as EInvoiceApiClient, so this is exactly what would go on the wire
		ObjectMapper writer = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
				.setVisibility(PropertyAccessor.ALL, Visibility.NONE)
				.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);

		InvoiceRequest request = reader.readValue(json, InvoiceRequest.class);

		// 1. Did every value in the file land in a model field?
		Set<String> in = leaves(reader.readTree(json));
		Set<String> out = leaves(writer.valueToTree(request));
		in.removeAll(out);
		System.out.println("== Model mapping ==");
		if (in.isEmpty())
			System.out.println("OK: every value in the file is present in the serialized request");
		else {
			System.out.println("NOT MAPPED (no matching model field):");
			in.forEach(p -> System.out.println("  " + p));
		}

		// 2. Official schema rules
		List<String> errors = IrpSchemaValidator.validate(request);
		System.out.println("\n== Schema validation ==");
		if (errors.isEmpty())
			System.out.println("OK: no violations");
		else
			errors.forEach(e -> System.out.println("  " + e));

		// 3. Outgoing payload
		System.out.println("\n== Request payload ==");
		System.out.println(writer.writerWithDefaultPrettyPrinter().writeValueAsString(request));

		if (!send)
			return;
		if (!errors.isEmpty()) {
			System.out.println("\nNot sending: fix the violations above first.");
			return;
		}

		if (!keepDocNo && request.getDocDtls() != null) {
			String no = "TST/" + new SimpleDateFormat("yyMMddHHmmss").format(new Date()); // 16 chars
			request.getDocDtls().setNo(no);
			System.out.println("\nDocDtls.No set to " + no + " (use --keep-docno to keep the file's value)");
		}

		System.out.println("\n== Authenticating ==");
		EInvoiceConfig cfg = EInvoiceConfig.fromEnvironment();   // before "== Authenticating =="
		EInvoiceAuthService auth = new EInvoiceAuthService(cfg);
		auth.authenticate();
		System.out.println("Authenticated as " + auth.getUserName());

		System.out.println("\n== GENERATE response ==");
		String body = new EInvoiceApiClient(cfg).generateIRN(auth.getAuthToken(), request);
		try {
			System.out.println(writer.writerWithDefaultPrettyPrinter().writeValueAsString(reader.readTree(body)));
		} catch (Exception e) {
			System.out.println(body);
		}
	}

	private static String readResource(String name) throws Exception {
		try (InputStream is = SampleRequestRunner.class.getResourceAsStream(name)) {
			if (is == null)
				throw new IllegalStateException(name + " not found on the classpath; pass a file path instead");
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	/** Lower-cased paths of all non-null leaf values. */
	private static Set<String> leaves(JsonNode root) {
		Set<String> s = new TreeSet<>();
		collect(root, "", s);
		return s;
	}

	private static void collect(JsonNode n, String path, Set<String> out) {
		if (n == null || n.isNull())
			return;
		if (n.isObject()) {
			Iterator<Map.Entry<String, JsonNode>> it = n.fields();
			while (it.hasNext()) {
				Map.Entry<String, JsonNode> e = it.next();
				collect(e.getValue(), path + "." + e.getKey().toLowerCase(), out);
			}
		} else if (n.isArray()) {
			for (int i = 0; i < n.size(); i++)
				collect(n.get(i), path + "[" + i + "]", out);
		} else
			out.add(path);
	}
}