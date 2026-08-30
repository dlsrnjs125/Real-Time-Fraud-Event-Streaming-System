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

    public void publish(TransactionEventMessage message, String sourceTopic) {
        String targetTopic = validateSourceTopic(sourceTopic);
        try {
            kafkaTemplate.send(targetTopic, message.userId(), message)
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

    private String validateSourceTopic(String sourceTopic) {
        if (KafkaTopicNames.TRANSACTION_EVENTS.equals(sourceTopic)
                || KafkaTopicNames.TRANSACTION_EVENTS_REPLAY.equals(sourceTopic)) {
            return sourceTopic;
        }
        throw new DeadLetterPublishFailedException(
                new IllegalArgumentException("unsupported DLT source topic: " + sourceTopic)
        );
    }
}
