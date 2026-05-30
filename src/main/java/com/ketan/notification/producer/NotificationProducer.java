package com.ketan.notification.producer;

import com.ketan.notification.model.NotificationRequest;

public interface NotificationProducer {
    /**
     * Publishes notification payload asynchronously to the queue.
     * Decouples the HTTP ingestion execution path from delivery provider dispatch.
     * 
     * @param request Validated notification request payload
     */
    void publishToQueue(NotificationRequest request);
}
