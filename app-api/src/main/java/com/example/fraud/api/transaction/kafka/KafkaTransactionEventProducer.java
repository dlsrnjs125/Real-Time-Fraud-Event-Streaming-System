package com.example.fraud.api.transaction.kafka;

import com.example.fraud.api.kafka.KafkaTopicNames;
import com.example.fraud.api.transaction.application.KafkaPublishFailedException;
import com.example.fraud.common.event.TransactionEventMessage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaTransactionEventProducer implements TransactionEventProducer {

    private final KafkaTemplate<String, TransactionEventMessage> kafkaTemplate;

    public KafkaTransactionEventProducer(KafkaTemplate<String, TransactionEventMessage> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(TransactionEventMessage message) {
        try {
            kafkaTemplate.send(KafkaTopicNames.TRANSACTION_EVENTS, message.userId(), message)
                    .get(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new KafkaPublishFailedException(exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new KafkaPublishFailedException(exception);
        } catch (Exception exception) {
            throw new KafkaPublishFailedException(exception);
        }
    }
}
