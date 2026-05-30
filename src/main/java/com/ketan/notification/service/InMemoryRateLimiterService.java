package com.ketan.notification.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
@Profile("mock")
public class InMemoryRateLimiterService implements RateLimiterService {

    @Value("${notification.rate-limit.requests-per-minute:60}")
    private int maxRequestsPerMinute;

    // Thread-safe map storing queues of timestamp millisecond values for each recipient
    private final ConcurrentHashMap<String, Queue<Long>> requestWindows = new ConcurrentHashMap<>();

    /**
     * Sliding Window Rate Limiting using in-memory queues.
     * Thread-safe and mirrors Redis sliding window ZSET.
     * 
     * @param recipientId Unique recipient identifier
     * @return true if the request is within rate limits, false otherwise
     */
    @Override
    public boolean isAllowed(String recipientId) {
        long now = Instant.now().toEpochMilli();
        long windowStart = now - 60000; // 60-second sliding window

        // Retrieve or create a thread-safe Queue of request timestamps for this user
        Queue<Long> timestamps = requestWindows.computeIfAbsent(recipientId, k -> new ConcurrentLinkedQueue<>());

        // Synchronize on the user's specific queue to ensure atomic read-and-write sliding operations
        synchronized (timestamps) {
            // 1. Remove all logs older than the 60-second sliding window
            while (!timestamps.isEmpty() && timestamps.peek() < windowStart) {
                timestamps.poll();
            }

            // 2. Count current elements within the sliding window
            if (timestamps.size() >= maxRequestsPerMinute) {
                // Rate limit exceeded
                return false;
            }

            // 3. Log the current request timestamp
            timestamps.add(now);
            return true;
        }
    }
}
