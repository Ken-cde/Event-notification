package com.ketan.notification.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Profile("!mock")
public class RedisRateLimiterService implements RateLimiterService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${notification.rate-limit.requests-per-minute:60}")
    private int maxRequestsPerMinute;

    /**
     * Sliding Window Rate Limiting using Redis Sorted Sets (ZSET).
     * This provides accurate boundary checks compared to a basic fixed-window approach.
     * 
     * @param recipientId Unique recipient identifier
     * @return true if the request is within rate limits, false otherwise
     */
    @Override
    public boolean isAllowed(String recipientId) {
        String key = "rate-limit:" + recipientId;
        long now = Instant.now().toEpochMilli();
        long windowStart = now - 60000; // 60-second sliding window

        try {
            // 1. Remove all logs older than the 60-second sliding window
            redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);

            // 2. Count current elements within the sliding window
            Long currentRequestCount = redisTemplate.opsForZSet().zCard(key);

            if (currentRequestCount != null && currentRequestCount >= maxRequestsPerMinute) {
                // Rate limit exceeded
                return false;
            }

            // 3. Log the current request timestamp as both value and score
            // Adding a random UUID suffix keeps the value unique in the sorted set
            String value = now + ":" + UUID.randomUUID().toString();
            redisTemplate.opsForZSet().add(key, value, now);

            // 4. Update key TTL to prevent Redis memory leak
            redisTemplate.expire(key, 60, TimeUnit.SECONDS);

            return true;
        } catch (Exception e) {
            // Fail open in case Redis is down to prevent blocking business traffic
            System.err.println("Redis Rate Limiter failed: " + e.getMessage());
            return true;
        }
    }
}
