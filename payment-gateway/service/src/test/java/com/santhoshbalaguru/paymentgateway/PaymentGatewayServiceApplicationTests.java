package com.santhoshbalaguru.paymentgateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Starts the whole application, so it needs the real PostgreSQL database and settings.
 * It runs when a .env file is present in the working directory or DB_PASSWORD is set,
 * and is skipped on a clean clone or in CI without them.
 */
@SpringBootTest
@EnabledIf("configured")
class PaymentGatewayServiceApplicationTests {

	static boolean configured() {
		return System.getenv("DB_PASSWORD") != null || Files.exists(Path.of(".env"));
	}

	@Test
	void contextLoads() {
	}

}