package com.example.fraud.api.admin.dlt;

import com.example.fraud.api.support.exception.ApiErrorCode;
import com.example.fraud.api.support.exception.ApiException;
import org.springframework.http.HttpStatus;

public class DeadLetterStateConflictException extends ApiException {

    public DeadLetterStateConflictException(long id, DeadLetterStatus status) {
        super(
                ApiErrorCode.DLT_STATE_CONFLICT,
                HttpStatus.CONFLICT,
                "dead letter event " + id + " cannot be changed from status " + status
        );
    }

    public DeadLetterStateConflictException(long id, String message) {
        super(
                ApiErrorCode.DLT_STATE_CONFLICT,
                HttpStatus.CONFLICT,
                "dead letter event " + id + " state conflict: " + message
        );
    }

    public DeadLetterStateConflictException(String message) {
        super(ApiErrorCode.DLT_STATE_CONFLICT, HttpStatus.CONFLICT, message);
    }
}
