package com.ketan.notification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RedisIdempotencyServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RedisIdempotencyService idempotencyService;

    @Test
    public void testAcquireLease_NewTransaction_ShouldReturnTrue() {
        // Arrange
        String txId = "tx_fresh";
        String key = "idempotency:" + txId;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(key), eq("PROCESSING"), eq(24L), eq(TimeUnit.HOURS))).thenReturn(true);

        // Act
        boolean result = idempotencyService.acquireLease(txId);

        // Assert
        assertTrue(result);
        verify(valueOperations).setIfAbsent(eq(key), eq("PROCESSING"), eq(24L), eq(TimeUnit.HOURS));
    }

    @Test
    public void testAcquireLease_DuplicateTransaction_ShouldReturnFalse() {
        // Arrange
        String txId = "tx_duplicate";
        String key = "idempotency:" + txId;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(key), eq("PROCESSING"), eq(24L), eq(TimeUnit.HOURS))).thenReturn(false);

        // Act
        boolean result = idempotencyService.acquireLease(txId);

        // Assert
        assertFalse(result);
    }
}
