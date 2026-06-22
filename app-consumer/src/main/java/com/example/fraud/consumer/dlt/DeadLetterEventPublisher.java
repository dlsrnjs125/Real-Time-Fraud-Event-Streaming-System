package com.example.fraud.consumer.dlt;

import com.example.fraud.common.dlt.DeadLetterEnvelope;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class DeadLetterEventPublisher {

    private final KafkaTemplate<String, DeadLetterEnvelope> kafkaTemplate;

    public DeadLetterEventPublisher(KafkaTemplate<String, DeadLetterEnvelope> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(DeadLetterEnvelope envelope, String dltTopic) {
        try {
            kafkaTemplate.send(dltTopic, envelope.eventId(), envelope).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DeadLetterPublishException(exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new DeadLetterPublishException(exception);
        } catch (Exception exception) {
            throw new DeadLetterPublishException(exception);
        }
    }
}
