package com.ketan.notification.service;

import com.ketan.notification.model.NotificationRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Profile("!mock")
public class KafkaDlqService implements DlqService {

    @Autowired
    private KafkaTemplate<String, NotificationRequest> kafkaTemplate;

    public static final String DLQ_TOPIC = "notification-dlq";

    @Override
    public void routeToDlq(NotificationRequest request, String errorReason) {
        System.err.println("CRITICAL: Routing transaction " + request.getTransactionId() 
            + " to Kafka DLQ. Reason: " + errorReason);
        kafkaTemplate.send(DLQ_TOPIC, request.getTransactionId(), request);
    }
}
