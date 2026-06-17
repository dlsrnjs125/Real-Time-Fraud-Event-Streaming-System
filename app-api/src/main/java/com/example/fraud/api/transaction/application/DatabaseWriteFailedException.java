package com.example.fraud.api.transaction.application;

import com.example.fraud.api.support.exception.ApiErrorCode;
import com.example.fraud.api.support.exception.ApiException;
import org.springframework.http.HttpStatus;

public class DatabaseWriteFailedException extends ApiException {

    public DatabaseWriteFailedException(Throwable cause) {
        super(ApiErrorCode.DATABASE_WRITE_FAILED, HttpStatus.INTERNAL_SERVER_ERROR, "failed to save transaction event receipt", cause);
    }
}
