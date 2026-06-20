package com.example.fraud.api.admin.fraud;

import com.example.fraud.api.support.exception.ApiErrorCode;
import com.example.fraud.api.support.exception.ApiException;
import org.springframework.http.HttpStatus;

public class FraudResultNotFoundException extends ApiException {

    public FraudResultNotFoundException(String eventId) {
        super(
                ApiErrorCode.FRAUD_RESULT_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                "fraud detection result not found for eventId: " + eventId
        );
    }
}
