package com.ketan.notification.service;

import com.ketan.notification.entity.NotificationLog;
import com.ketan.notification.model.NotificationRequest;
import com.ketan.notification.model.NotificationStatus;
import com.ketan.notification.repository.NotificationLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class NotificationDeliveryService {

    @Autowired
    private NotificationLogRepository auditLogRepository;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private DlqService dlqService;

    @Value("${notification.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${notification.retry.backoff-interval-ms:1000}")
    private long backoffIntervalMs;

    /**
     * Executes the delivery flow for a notification request.
     * Retries with exponential backoff and saves state transitions to DB.
     * Routes permanently failed payloads to the active DLQ service.
     * 
     * @param request Ingested notification request
     */
    public void deliver(NotificationRequest request) {
        System.out.println("Processing notification event for transaction: " + request.getTransactionId());

        // 1. Retrieve existing audit record or create one if missing (robust fallback)
        NotificationLog log = auditLogRepository.findByTransactionId(request.getTransactionId())
            .orElseGet(() -> NotificationLog.builder()
                .transactionId(request.getTransactionId())
                .recipientId(request.getRecipientId())
                .channel(request.getChannel())
                .destination(request.getDestination())
                .subject(request.getSubject())
                .message(request.getMessage())
                .status(NotificationStatus.PROCESSING)
                .retryCount(0)
                .build());

        log.setStatus(NotificationStatus.PROCESSING);
        auditLogRepository.save(log);

        boolean deliverySuccess = false;
        int attempts = 0;
        String lastError = "";

        // 2. Retry Loop with Exponential Backoff
        while (attempts < maxAttempts && !deliverySuccess) {
            attempts++;
            log.setRetryCount(attempts);
            try {
                // Simulate third-party delivery dispatch (e.g. Twilio, SendGrid)
                dispatchToProvider(request);
                deliverySuccess = true;
            } catch (Exception e) {
                lastError = e.getMessage();
                System.err.println("Delivery attempt #" + attempts + " failed for transaction: " 
                    + request.getTransactionId() + " - " + lastError);
                if (attempts < maxAttempts) {
                    try {
                        // Exponential backoff: backoffInterval * (2^(attempts - 1))
                        long waitTime = backoffIntervalMs * (long) Math.pow(2, attempts - 1);
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // 3. Post-Delivery State Machine Update
        if (deliverySuccess) {
            log.setStatus(NotificationStatus.DELIVERED);
            log.setFailureReason(null);
            auditLogRepository.save(log);
            idempotencyService.updateStatus(request.getTransactionId(), "DELIVERED");
            System.out.println("Notification successfully delivered: " + request.getTransactionId());
        } else {
            // Permanently failed, route to DLQ
            log.setStatus(NotificationStatus.DLQ);
            log.setFailureReason(lastError);
            auditLogRepository.save(log);
            idempotencyService.updateStatus(request.getTransactionId(), "FAILED");
            
            // Route to DLQ (Kafka DLQ topic or In-Memory DLQ cache)
            dlqService.routeToDlq(request, lastError);
        }
    }

    /**
     * Simulates integration with SMTP email relays, SMS aggregators, or WebPush systems.
     * Throws an exception 15% of the time under high-load simulation to demonstrate retry handling.
     */
    private void dispatchToProvider(NotificationRequest request) throws Exception {
        // 15% random failure probability
        if (Math.random() < 0.15) {
            throw new Exception("Connection timeout while contacting gateway provider");
        }
        // Success execution pathway
        Thread.sleep(30); // Simulate network latency to external provider
    }
}
