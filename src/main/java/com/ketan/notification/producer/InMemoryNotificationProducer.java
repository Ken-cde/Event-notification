package com.ketan.notification.producer;

import com.ketan.notification.model.NotificationRequest;
import com.ketan.notification.service.NotificationDeliveryService;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@Profile("mock")
public class InMemoryNotificationProducer implements NotificationProducer {

    @Autowired
    private NotificationDeliveryService deliveryService;

    // Async thread executor simulating decoupled message queue workers
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    /**
     * Publishes notification payload asynchronously to the local in-memory worker thread pool.
     * 
     * @param request Validated notification request payload
     */
    @Override
    public void publishToQueue(NotificationRequest request) {
        System.out.println("MOCK QUEUE: Accepted and queued transaction in-memory: " + request.getTransactionId());
        
        executorService.submit(() -> {
            try {
                // Simulate quick broker processing latency (20ms) before worker thread consumes
                Thread.sleep(20);
                deliveryService.deliver(request);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("In-memory queue processing interrupted for transaction: " + request.getTransactionId());
            }
        });
    }

    /**
     * Clean shutdown of background threads when Spring container shuts down.
     */
    @PreDestroy
    public void shutdown() {
        System.out.println("Shutting down in-memory queue executor service...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(3, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
