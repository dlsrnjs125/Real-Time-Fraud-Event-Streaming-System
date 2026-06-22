package com.example.fraud.api.admin.dlt;

import com.example.fraud.api.support.exception.ApiErrorCode;
import com.example.fraud.api.support.exception.ApiException;
import org.springframework.http.HttpStatus;

public class DeadLetterPublishFailedException extends ApiException {

    public DeadLetterPublishFailedException(Throwable cause) {
        super(
                ApiErrorCode.KAFKA_PUBLISH_FAILED,
                HttpStatus.SERVICE_UNAVAILABLE,
                "failed to publish dead letter event for reprocessing",
                cause
        );
    }
}
