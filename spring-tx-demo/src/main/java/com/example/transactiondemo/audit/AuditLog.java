package com.example.transactiondemo.audit;

public class AuditLog {

    private Long id;
    private String message;

    public AuditLog() {
    }

    public AuditLog(String message) {
        this.message = message;
    }

    public Long getId() {
        return id;
    }

    public String getMessage() {
        return message;
    }
}
