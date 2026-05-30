package com.ketan.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RedisRateLimiterServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @InjectMocks
    private RedisRateLimiterService rateLimiterService;

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(rateLimiterService, "maxRequestsPerMinute", 3);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    @Test
    public void testIsAllowed_UnderLimit_ShouldReturnTrue() {
        // Arrange
        String recipientId = "user_abc";
        String key = "rate-limit:" + recipientId;

        when(zSetOperations.removeRangeByScore(eq(key), anyDouble(), anyDouble())).thenReturn(1L);
        when(zSetOperations.zCard(eq(key))).thenReturn(2L); // 2 requests already within window (limit is 3)
        when(zSetOperations.add(eq(key), anyString(), anyDouble())).thenReturn(true);
        when(redisTemplate.expire(eq(key), anyLong(), any())).thenReturn(true);

        // Act
        boolean allowed = rateLimiterService.isAllowed(recipientId);

        // Assert
        assertTrue(allowed);
        verify(zSetOperations).removeRangeByScore(eq(key), anyDouble(), anyDouble());
        verify(zSetOperations).zCard(eq(key));
        verify(zSetOperations).add(eq(key), anyString(), anyDouble());
    }

    @Test
    public void testIsAllowed_OverLimit_ShouldReturnFalse() {
        // Arrange
        String recipientId = "user_spam";
        String key = "rate-limit:" + recipientId;

        when(zSetOperations.removeRangeByScore(eq(key), anyDouble(), anyDouble())).thenReturn(1L);
        when(zSetOperations.zCard(eq(key))).thenReturn(3L); // Already at maximum threshold limit of 3

        // Act
        boolean allowed = rateLimiterService.isAllowed(recipientId);

        // Assert
        assertFalse(allowed);
        verify(zSetOperations, never()).add(eq(key), anyString(), anyDouble());
    }
}
