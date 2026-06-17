package com.example.fraud.api.transaction.application;

import com.example.fraud.api.support.exception.ApiErrorCode;
import com.example.fraud.api.support.exception.ApiException;
import org.springframework.http.HttpStatus;

public class DuplicateTransactionEventException extends ApiException {

    public DuplicateTransactionEventException(String eventId) {
        super(ApiErrorCode.DUPLICATE_TRANSACTION_EVENT, HttpStatus.CONFLICT, "duplicate transaction event: " + eventId);
    }
}
