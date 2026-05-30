package com.ketan.notification.model;

public enum NotificationStatus {
    INGESTED,      // Request received and validated, written to Kafka queue
    RATE_LIMITED,  // Dropped due to recipient exceeding spam thresholds
    DEDUPLICATED,  // Dropped as a duplicate of an already processed transaction ID
    PROCESSING,    // Pulled from Kafka and delivery is being attempted
    DELIVERED,     // Successfully dispatched through third party provider mock
    FAILED,        // Permanently failed after exhausts retries
    DLQ            // Routed to Dead Letter Queue for manual investigation
}
