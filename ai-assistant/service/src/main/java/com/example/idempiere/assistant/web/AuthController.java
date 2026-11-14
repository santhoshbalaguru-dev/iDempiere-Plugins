package com.example.idempiere.assistant.web;

import com.example.idempiere.assistant.idempiere.IdempiereClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Standalone login for Phase 1. The iDempiere plugin replaces this in Phase 6. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final IdempiereClient idempiere;

    public AuthController(IdempiereClient idempiere) {
        this.idempiere = idempiere;
    }

    public record LoginRequest(@NotBlank String userName, @NotBlank String password,
                               @NotNull Integer clientId, @NotNull Integer roleId,
                               @NotNull Integer organizationId, Integer warehouseId,
                               String language) {}

    public record LoginResponse(String token) {}

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        String pre = idempiere.login(req.userName(), req.password());
        String language = req.language() == null || req.language().isBlank() ? "en_US" : req.language();
        String scoped = idempiere.selectContext(pre, req.clientId(), req.roleId(),
                req.organizationId(), req.warehouseId(), language);
        return new LoginResponse(scoped);
    }
}
