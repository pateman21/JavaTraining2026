package com.example.banking.exception;

public class PaymentProcessorException extends RuntimeException {
    public PaymentProcessorException(String message) {
        super(message);
    }

    public PaymentProcessorException(String message, Throwable cause) {
        super(message, cause);
    }
}
