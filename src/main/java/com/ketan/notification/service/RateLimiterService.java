package com.ketan.notification.service;

public interface RateLimiterService {
    /**
     * Checks if a request is allowed for the given recipient under current rate limit rules.
     * 
     * @param recipientId Unique recipient identifier
     * @return true if the request is within rate limits, false otherwise
     */
    boolean isAllowed(String recipientId);
}
