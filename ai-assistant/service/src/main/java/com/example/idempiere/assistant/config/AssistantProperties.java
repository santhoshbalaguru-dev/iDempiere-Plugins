package com.example.idempiere.assistant.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "assistant")
public record AssistantProperties(Idempiere idempiere, Chat chat, Cors cors) {

    public record Idempiere(String baseUrl, Duration timeout, Duration verifyTtl, String verifyPath) {}

    public record Chat(int memoryMessages) {}

    public record Cors(List<String> allowedOrigins) {}
}
