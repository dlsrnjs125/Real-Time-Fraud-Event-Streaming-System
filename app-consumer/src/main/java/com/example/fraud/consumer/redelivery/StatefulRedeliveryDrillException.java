package com.example.fraud.consumer.redelivery;

public class StatefulRedeliveryDrillException extends RuntimeException {

    public StatefulRedeliveryDrillException(String message) {
        super(message);
    }
}
