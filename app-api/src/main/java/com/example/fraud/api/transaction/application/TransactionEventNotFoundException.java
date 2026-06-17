package com.example.fraud.api.transaction.application;

import com.example.fraud.api.support.exception.ApiErrorCode;
import com.example.fraud.api.support.exception.ApiException;
import org.springframework.http.HttpStatus;

public class TransactionEventNotFoundException extends ApiException {

    public TransactionEventNotFoundException(String eventId) {
        super(ApiErrorCode.EVENT_NOT_FOUND, HttpStatus.NOT_FOUND, "transaction event not found: " + eventId);
    }
}
