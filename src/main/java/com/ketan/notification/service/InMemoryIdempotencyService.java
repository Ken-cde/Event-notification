package com.ketan.notification.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Profile("mock")
public class InMemoryIdempotencyService implements IdempotencyService {

    // Thread-safe map to store transaction states in memory
    private final Map<String, String> transactionCache = new ConcurrentHashMap<>();

    /**
     * Attempts to acquire an idempotency lease for the given transactionId.
     * Thread-safe and atomic in-memory check.
     * 
     * @param transactionId Unique client-provided transaction identifier
     * @return true if this is a fresh transaction, false if it is a duplicate
     */
    @Override
    public boolean acquireLease(String transactionId) {
        // putIfAbsent returns null if the key was not previously mapped
        String existingValue = transactionCache.putIfAbsent(transactionId, "PROCESSING");
        return existingValue == null;
    }

    /**
     * Updates the status of the transaction in the in-memory cache.
     */
    @Override
    public void updateStatus(String transactionId, String status) {
        transactionCache.put(transactionId, status);
    }
}
