package com.example.fraud.api.support.exception;

import org.springframework.http.HttpStatus;

public abstract class ApiException extends RuntimeException {

    private final ApiErrorCode errorCode;
    private final HttpStatus status;

    protected ApiException(ApiErrorCode errorCode, HttpStatus status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    protected ApiException(ApiErrorCode errorCode, HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.status = status;
    }

    public ApiErrorCode errorCode() {
        return errorCode;
    }

    public HttpStatus status() {
        return status;
    }
}
