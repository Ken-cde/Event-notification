package com.ketan.notification.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

public class InMemoryServicesTest {

    @Test
    public void testInMemoryIdempotencyService() {
        InMemoryIdempotencyService service = new InMemoryIdempotencyService();

        // First attempt should succeed (lease acquired)
        assertTrue(service.acquireLease("tx_1"));
        
        // Second attempt with same txId should fail (duplicate block)
        assertFalse(service.acquireLease("tx_1"));

        // Update state and verify we can update
        service.updateStatus("tx_1", "DELIVERED");
        
        // A fresh transaction ID should succeed
        assertTrue(service.acquireLease("tx_2"));
    }

    @Test
    public void testInMemoryRateLimiterService() {
        InMemoryRateLimiterService service = new InMemoryRateLimiterService();
        ReflectionTestUtils.setField(service, "maxRequestsPerMinute", 3);

        // Allow first 3 requests within the minute
        assertTrue(service.isAllowed("user_1"));
        assertTrue(service.isAllowed("user_1"));
        assertTrue(service.isAllowed("user_1"));

        // 4th request from same user should exceed limit and be blocked
        assertFalse(service.isAllowed("user_1"));

        // A different user should be allowed (isolated sliding windows)
        assertTrue(service.isAllowed("user_2"));
    }
}
