package com.ketan.notification.controller;

import com.ketan.notification.entity.NotificationLog;
import com.ketan.notification.model.NotificationRequest;
import com.ketan.notification.model.NotificationStatus;
import com.ketan.notification.producer.NotificationProducer;
import com.ketan.notification.repository.NotificationLogRepository;
import com.ketan.notification.service.IdempotencyService;
import com.ketan.notification.service.RateLimiterService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private NotificationProducer notificationProducer;

    @Autowired
    private NotificationLogRepository auditLogRepository;

    /**
     * Ingests a new notification request payload.
     * Enforces idempotency (24h window) and sliding-window rate-limiting.
     * 
     * @param request The notification payload
     * @return 202 Accepted on queueing, 429 on rate limit, or 200 on deduplicated event
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> sendNotification(@Valid @RequestBody NotificationRequest request) {
        Map<String, Object> response = new HashMap<>();

        // 1. Idempotency Check (Deduplication)
        boolean freshTransaction = idempotencyService.acquireLease(request.getTransactionId());
        if (!freshTransaction) {
            // Deduplicate to avoid dual message deliveries
            response.put("status", NotificationStatus.DEDUPLICATED);
            response.put("transactionId", request.getTransactionId());
            response.put("message", "Duplicate transaction detected and skipped for delivery safety.");
            
            // Log in PostgreSQL
            auditLogRepository.findByTransactionId(request.getTransactionId()).ifPresentOrElse(
                existingLog -> {
                    // Update state to match deduplication
                    System.out.println("Deduplicated incoming transaction ID: " + request.getTransactionId());
                },
                () -> {
                    // Record in DB
                    NotificationLog log = NotificationLog.builder()
                        .transactionId(request.getTransactionId())
                        .recipientId(request.getRecipientId())
                        .channel(request.getChannel())
                        .destination(request.getDestination())
                        .subject(request.getSubject())
                        .message(request.getMessage())
                        .status(NotificationStatus.DEDUPLICATED)
                        .failureReason("Duplicate transaction ID block")
                        .build();
                    auditLogRepository.save(log);
                }
            );

            return ResponseEntity.ok(response);
        }

        // 2. Sliding Window Rate Limiting Check
        boolean withinRateLimit = rateLimiterService.isAllowed(request.getRecipientId());
        if (!withinRateLimit) {
            // Cancel idempotency lease so user can retry later
            idempotencyService.updateStatus(request.getTransactionId(), "RATE_LIMITED");

            response.put("status", NotificationStatus.RATE_LIMITED);
            response.put("transactionId", request.getTransactionId());
            response.put("message", "Rate limit exceeded. Maximum allowed messages reached for recipient.");

            // Log in PostgreSQL
            NotificationLog log = NotificationLog.builder()
                .transactionId(request.getTransactionId())
                .recipientId(request.getRecipientId())
                .channel(request.getChannel())
                .destination(request.getDestination())
                .subject(request.getSubject())
                .message(request.getMessage())
                .status(NotificationStatus.RATE_LIMITED)
                .failureReason("Recipient exceeded rate limits")
                .build();
            auditLogRepository.save(log);

            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
        }

        // 3. Initial Audit Record
        NotificationLog log = NotificationLog.builder()
            .transactionId(request.getTransactionId())
            .recipientId(request.getRecipientId())
            .channel(request.getChannel())
            .destination(request.getDestination())
            .subject(request.getSubject())
            .message(request.getMessage())
            .status(NotificationStatus.INGESTED)
            .retryCount(0)
            .build();
        auditLogRepository.save(log);

        // 4. Publish Event asynchronously to Kafka
        notificationProducer.publishToQueue(request);

        response.put("status", NotificationStatus.INGESTED);
        response.put("transactionId", request.getTransactionId());
        response.put("message", "Notification accepted and queued successfully.");

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
