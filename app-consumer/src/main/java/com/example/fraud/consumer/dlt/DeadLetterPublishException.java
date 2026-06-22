package com.example.fraud.consumer.dlt;

public class DeadLetterPublishException extends RuntimeException {

    public DeadLetterPublishException(Throwable cause) {
        super("failed to publish dead letter event", cause);
    }
}
