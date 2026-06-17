package com.example.fraud.api.transaction.application;

import com.example.fraud.api.support.exception.ApiErrorCode;
import com.example.fraud.api.support.exception.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidTransactionEventException extends ApiException {

    public InvalidTransactionEventException(String message) {
        super(ApiErrorCode.INVALID_TRANSACTION_EVENT, HttpStatus.BAD_REQUEST, message);
    }
}
