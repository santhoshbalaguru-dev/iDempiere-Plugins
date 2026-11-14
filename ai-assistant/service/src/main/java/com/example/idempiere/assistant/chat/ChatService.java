package com.example.idempiere.assistant.chat;

import com.example.idempiere.assistant.config.AssistantProperties;
import com.example.idempiere.assistant.security.TokenVerifier;
import com.example.idempiere.assistant.tools.PurchaseOrderTools;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    /** Key under which the caller's iDempiere token travels to tools via ToolContext. */
    public static final String TOKEN_KEY = "idempiereToken";

    private final ChatClient chatClient;
    private final ConversationRegistry conversations;
    private final String systemTemplate;

    public ChatService(ChatClient.Builder builder,
                       PurchaseOrderTools purchaseOrderTools,
                       ConversationRegistry conversations,
                       AssistantProperties props,
                       @Value("classpath:prompts/system.txt") Resource systemPrompt) {
        this.conversations = conversations;
        try {
            this.systemTemplate = systemPrompt.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ChatMemory memory = MessageWindowChatMemory.builder()
                .maxMessages(props.chat().memoryMessages())
                .build();
        this.chatClient = builder
                .defaultTools(purchaseOrderTools)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .build();
    }

    public ChatReply chat(String token, String conversationId, String message) {
        String id = conversations.resolve(conversationId, TokenVerifier.fingerprint(token));
        String answer = chatClient.prompt()
                .system(s -> s.text(systemTemplate).param("today", LocalDate.now().toString()))
                .user(message)
                .toolContext(Map.of(TOKEN_KEY, token))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, id))
                .call()
                .content();
        return new ChatReply(id, answer);
    }

    public record ChatReply(String conversationId, String answer) {}
}
