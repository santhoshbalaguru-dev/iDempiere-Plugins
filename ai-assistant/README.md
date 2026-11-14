# iDempiere AI Assistant

Natural-language questions over iDempiere ERP data. Phase 1: Spring Boot + Spring AI service
that answers with tool calls through the iDempiere REST API, so iDempiere enforces the user's
role, client and org access.

## Layout

    service/   Spring Boot 3.4, Spring AI 1.0, Java 21
    deploy/    docker-compose.yml, .env.example
    (plugin/ and web/ arrive in later phases)

## Prerequisites

- iDempiere 13 running with the REST API reachable at `http://localhost:8080/api`
  (the login call below confirms it)
- JDK 21 and Maven 3.9+
- Either an OpenAI API key, or Ollama with a tool-capable model: `ollama pull llama3.1`

## Run locally (PowerShell)

    cd service
    mvn test

    # Ollama (default profile)
    mvn spring-boot:run

    # OpenAI
    $env:OPENAI_API_KEY = "sk-..."
    mvn spring-boot:run "-Dspring-boot.run.profiles=openai"

## Try it against GardenWorld

The IDs below are the GardenWorld sample data. Adjust them if yours differ.

    $login = @{ userName="GardenAdmin"; password="GardenAdmin"; clientId=11; roleId=102; organizationId=11; warehouseId=103 } | ConvertTo-Json
    $r = Invoke-RestMethod -Method Post -Uri http://localhost:8081/api/v1/auth/login -ContentType "application/json" -Body $login

    $chat = @{ message = "Show pending purchase orders" } | ConvertTo-Json
    $a = Invoke-RestMethod -Method Post -Uri http://localhost:8081/api/v1/chat -Headers @{ Authorization = "Bearer $($r.token)" } -ContentType "application/json" -Body $chat
    $a.answer

Pass `$a.conversationId` back as `conversationId` to continue the conversation.

## Docker

    cd deploy
    copy .env.example .env
    docker compose --profile ollama up --build
    docker compose exec ollama ollama pull llama3.1

## Behaviour to know about

- "Pending" purchase orders means Drafted (DR) or In Progress (IP). Change `PENDING_FILTER`
  in `PurchaseOrderTools` if your process differs.
- Conversation memory is in-process and bound to the token that started it. Re-logging in
  starts a new conversation. Persistent memory comes later.
- The service verifies each bearer token against iDempiere (cached for 60 s) before calling the LLM.
  `assistant.idempiere.verify-path` sets which endpoint is used.

## Roadmap

1. Foundation (this) 2. Core tools + audit log 3. React chat UI with streaming
4. Guarded NL-to-SQL + evaluation harness 5. Reports and PDF/Excel export
6. iDempiere plugin (ZK embed, SSO token) 7. Hardening and release
