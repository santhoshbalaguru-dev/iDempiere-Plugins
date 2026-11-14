package com.example.idempiere.assistant.web;

import com.example.idempiere.assistant.chat.ChatService;
import com.example.idempiere.assistant.chat.ChatService.ChatReply;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    public record ChatRequest(@NotBlank @Size(max = 2000) String message, String conversationId) {}

    @PostMapping
    public ChatReply chat(@Valid @RequestBody ChatRequest req, Authentication auth) {
        String token = (String) auth.getCredentials();
        return chat.chat(token, req.conversationId(), req.message());
    }
}
