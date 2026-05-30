package com.ketan.notification.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Service
@Profile("!mock")
public class RedisIdempotencyService implements IdempotencyService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "idempotency:";
    private static final long LOCK_EXPIRATION_HOURS = 24;

    /**
     * Attempts to acquire an idempotency lease for the given transactionId.
     * Uses Redis SETNX (Set if Not Exists) to ensure atomic checks.
     * 
     * @param transactionId Unique client-provided transaction identifier
     * @return true if this is a fresh transaction, false if it is a duplicate
     */
    @Override
    public boolean acquireLease(String transactionId) {
        String key = KEY_PREFIX + transactionId;
        try {
            // SETNX with a 24-hour expiration atomic operation
            Boolean success = redisTemplate.opsForValue().setIfAbsent(
                key, 
                "PROCESSING", 
                LOCK_EXPIRATION_HOURS, 
                TimeUnit.HOURS
            );
            return success != null && success;
        } catch (Exception e) {
            // Fail open, but log warning
            System.err.println("Redis Idempotency Check failed: " + e.getMessage());
            return true;
        }
    }

    /**
     * Updates the status of the transaction in Redis to keep track of execution outcome.
     */
    @Override
    public void updateStatus(String transactionId, String status) {
        String key = KEY_PREFIX + transactionId;
        try {
            redisTemplate.opsForValue().set(key, status, LOCK_EXPIRATION_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            System.err.println("Redis Idempotency Status Update failed: " + e.getMessage());
        }
    }
}
