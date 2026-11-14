package com.santhoshbalaguru.paymentgateway.gateway;

public class InvalidSignatureException extends RuntimeException {

    public InvalidSignatureException(String message) {
        super(message);
    }
}