package com.example.fraud.api.admin.dlt;

import com.example.fraud.api.support.exception.ApiErrorCode;
import com.example.fraud.api.support.exception.ApiException;
import org.springframework.http.HttpStatus;

public class DeadLetterEventNotFoundException extends ApiException {

    public DeadLetterEventNotFoundException(long id) {
        super(ApiErrorCode.DLQ_EVENT_NOT_FOUND, HttpStatus.NOT_FOUND, "dead letter event not found: " + id);
    }
}
