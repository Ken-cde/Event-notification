package com.ketan.notification.consumer;

import com.ketan.notification.model.NotificationRequest;
import com.ketan.notification.service.NotificationDeliveryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Profile("!mock")
public class KafkaNotificationConsumer {

    @Autowired
    private NotificationDeliveryService deliveryService;

    /**
     * Consumes incoming notification events from the Apache Kafka broker.
     * Delegates core processing, retry mechanics, and state reporting to Delivery Service.
     * 
     * @param request Received notification payload from Kafka
     */
    @KafkaListener(topics = "notification-ingested", groupId = "notification-consumers")
    public void consumeNotification(NotificationRequest request) {
        deliveryService.deliver(request);
    }
}
