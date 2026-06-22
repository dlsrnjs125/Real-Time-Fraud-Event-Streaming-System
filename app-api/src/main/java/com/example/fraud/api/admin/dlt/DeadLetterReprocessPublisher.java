package com.example.fraud.api.admin.dlt;

import com.example.fraud.api.kafka.KafkaTopicNames;
import com.example.fraud.common.event.TransactionEventMessage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class DeadLetterReprocessPublisher {

    private final KafkaTemplate<String, TransactionEventMessage> kafkaTemplate;

    public DeadLetterReprocessPublisher(KafkaTemplate<String, TransactionEventMessage> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(TransactionEventMessage message) {
        try {
            kafkaTemplate.send(KafkaTopicNames.TRANSACTION_EVENTS, message.userId(), message)
                    .get(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DeadLetterPublishFailedException(exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new DeadLetterPublishFailedException(exception);
        } catch (Exception exception) {
            throw new DeadLetterPublishFailedException(exception);
        }
    }
}
