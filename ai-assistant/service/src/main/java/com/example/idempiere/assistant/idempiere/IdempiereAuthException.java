package com.example.idempiere.assistant.idempiere;

/** iDempiere rejected the credentials or the bearer token (HTTP 401/403). */
public class IdempiereAuthException extends RuntimeException {
    public IdempiereAuthException(String message) {
        super(message);
    }
}
