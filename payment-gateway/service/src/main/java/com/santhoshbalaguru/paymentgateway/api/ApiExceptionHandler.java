package com.santhoshbalaguru.paymentgateway.api;

import com.santhoshbalaguru.paymentgateway.gateway.GatewayException;
import com.santhoshbalaguru.paymentgateway.service.ConflictException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.santhoshbalaguru.paymentgateway.gateway.InvalidSignatureException;
import com.santhoshbalaguru.paymentgateway.service.UnknownLinkException;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    public record ErrorBody(String error, Object details) {
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> invalid(MethodArgumentNotValidException e) {
        Map<String, String> details = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField,
                        fe -> String.valueOf(fe.getDefaultMessage()), (a, b) -> a));
        return ResponseEntity.badRequest().body(new ErrorBody("validation_failed", details));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorBody> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorBody("bad_request", e.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ErrorBody> conflict(ConflictException e) {
        return ResponseEntity.status(409).body(new ErrorBody("conflict", e.getMessage()));
    }

    @ExceptionHandler(GatewayException.class)
    ResponseEntity<ErrorBody> gateway(GatewayException e) {
        return ResponseEntity.status(502).body(new ErrorBody("gateway_error", e.getMessage()));
    }

        @ExceptionHandler(InvalidSignatureException.class)
    ResponseEntity<ErrorBody> signature(InvalidSignatureException e) {
        return ResponseEntity.status(401).body(new ErrorBody("invalid_signature", null));
    }

        @ExceptionHandler(UnknownLinkException.class)
    ResponseEntity<ErrorBody> unknownLink(UnknownLinkException e) {
        return ResponseEntity.status(503).body(new ErrorBody("unknown_link", e.getMessage()));
    }
}