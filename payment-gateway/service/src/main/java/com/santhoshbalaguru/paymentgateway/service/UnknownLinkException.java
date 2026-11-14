package com.santhoshbalaguru.paymentgateway.service;

public class UnknownLinkException extends RuntimeException {

    public UnknownLinkException(String message) {
        super(message);
    }
}