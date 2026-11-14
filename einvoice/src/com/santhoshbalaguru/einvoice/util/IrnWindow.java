package com.santhoshbalaguru.einvoice.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** The IRP's 24-hour cancellation window, measured from the acknowledgement time (IST). */
public final class IrnWindow {

	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
	private static final DateTimeFormatter ACK_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private IrnWindow() {
	}

	/** True while the IRN can still be cancelled. A blank or unreadable date counts as inside, so the IRP decides. */
	public static boolean isWithin24Hours(String ackDt) {
		if (ackDt == null || ackDt.isBlank())
			return true;
		try {
			ZonedDateTime registered = LocalDateTime.parse(ackDt.trim(), ACK_FMT).atZone(IST);
			return !ZonedDateTime.now(IST).isAfter(registered.plusHours(24));
		} catch (DateTimeParseException e) {
			return true;
		}
	}
}