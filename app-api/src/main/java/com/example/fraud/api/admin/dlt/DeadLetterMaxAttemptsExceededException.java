package com.example.fraud.api.admin.dlt;

import com.example.fraud.api.support.exception.ApiErrorCode;
import com.example.fraud.api.support.exception.ApiException;
import org.springframework.http.HttpStatus;

public class DeadLetterMaxAttemptsExceededException extends ApiException {

    public DeadLetterMaxAttemptsExceededException(long id, int attempts, int maxAttempts) {
        super(
                ApiErrorCode.MAX_REPROCESS_ATTEMPTS_EXCEEDED,
                HttpStatus.CONFLICT,
                "dead letter event " + id + " has reached max reprocess attempts " + maxAttempts
                        + " (current attempts: " + attempts + ")"
        );
    }
}
