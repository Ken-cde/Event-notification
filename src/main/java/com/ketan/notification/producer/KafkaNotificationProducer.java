package com.ketan.notification.producer;

import com.ketan.notification.model.NotificationRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Profile("!mock")
public class KafkaNotificationProducer implements NotificationProducer {

    @Autowired
    private KafkaTemplate<String, NotificationRequest> kafkaTemplate;

    public static final String INGESTED_TOPIC = "notification-ingested";

    /**
     * Publishes notification payload asynchronously to Apache Kafka.
     * Decouples the HTTP ingestion execution path from provider dispatch.
     * 
     * @param request Validated notification request payload
     */
    @Override
    public void publishToQueue(NotificationRequest request) {
        // Use transactionId as the message key to preserve order per-transaction or per-recipient
        kafkaTemplate.send(INGESTED_TOPIC, request.getTransactionId(), request)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    System.err.println("Failed to publish message to Kafka for transaction: " 
                        + request.getTransactionId() + " due to: " + ex.getMessage());
                } else {
                    System.out.println("Successfully published message to Kafka for transaction: " 
                        + request.getTransactionId() + " on partition: " + result.getRecordMetadata().partition());
                }
            });
    }
}
