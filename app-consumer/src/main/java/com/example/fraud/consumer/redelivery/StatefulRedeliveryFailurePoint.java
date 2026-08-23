package com.example.fraud.consumer.redelivery;

public enum StatefulRedeliveryFailurePoint {
    NONE,
    BEFORE_REDIS_UPDATE,
    AFTER_REDIS_UPDATE_BEFORE_RESULT,
    AFTER_RESULT_SAVE_BEFORE_ACK
}
