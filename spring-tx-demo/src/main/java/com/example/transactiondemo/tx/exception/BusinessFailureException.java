package com.example.transactiondemo.tx.exception;

public class BusinessFailureException extends RuntimeException {

    public BusinessFailureException(String message) {
        super(message);
    }
}
