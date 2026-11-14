package com.santhoshbalaguru.einvoice.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.santhoshbalaguru.einvoice.config.EInvoiceConfig;

/**
 * Keeps the IRP auth token until shortly before it expires, so single-invoice runs
 * and the cancel process do not log in every time. Held in memory only (lost on restart).
 */
public final class EInvoiceTokenCache {

	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
	private static final DateTimeFormatter EXPIRY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	/** Log in again this long before the stated expiry. */
	private static final Duration MARGIN = Duration.ofMinutes(10);

	private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();

	private static final class Entry {
		final String token;
		final Instant expires;

		Entry(String token, Instant expires) {
			this.token = token;
			this.expires = expires;
		}
	}

	private EInvoiceTokenCache() {
	}

	/** Cached token while it is still good, otherwise a fresh login. forceNew skips the cache. */
	public static synchronized String get(EInvoiceConfig cfg, boolean forceNew) throws Exception {
		String key = key(cfg);
		Entry e = CACHE.get(key);
		if (!forceNew && e != null && Instant.now().isBefore(e.expires.minus(MARGIN)))
			return e.token;

		CACHE.remove(key);
		EInvoiceAuthService auth = new EInvoiceAuthService(cfg);
		auth.authenticate();
		String token = auth.getAuthToken();

		Instant expires = parseExpiry(auth.getAuthData().getTokenExpiry());
		if (expires != null)          // unreadable expiry: do not cache, log in next time
			CACHE.put(key, new Entry(token, expires));
		return token;
	}

	/** True when the IRP said the token is invalid or expired. */
	public static boolean isTokenProblem(Throwable e) {
		String m = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
		return m.contains("token") && (m.contains("invalid") || m.contains("expired"));
	}

	private static String key(EInvoiceConfig cfg) {
		return cfg.id + "|" + cfg.gstin + "|" + cfg.baseUrl + "|" + cfg.userName + "|" + cfg.clientId;
	}

	private static Instant parseExpiry(String s) {
		if (s == null || s.isBlank())
			return null;
		try {
			return LocalDateTime.parse(s.trim(), EXPIRY_FMT).atZone(IST).toInstant();
		} catch (DateTimeParseException ex) {
			return null;
		}
	}
}