package com.example.fraud.api.transaction.application;

import com.example.fraud.api.support.exception.ApiErrorCode;
import com.example.fraud.api.support.exception.ApiException;
import org.springframework.http.HttpStatus;

public class KafkaPublishFailedException extends ApiException {

    public KafkaPublishFailedException(Throwable cause) {
        super(ApiErrorCode.KAFKA_PUBLISH_FAILED, HttpStatus.SERVICE_UNAVAILABLE, "failed to publish transaction event", cause);
    }
}
