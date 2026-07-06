package com.example.restaurant;

public class ApiException extends RuntimeException {
    public ApiException(String message) {
        super(message);
    }
}
