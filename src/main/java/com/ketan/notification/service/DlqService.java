package com.ketan.notification.service;

import com.ketan.notification.model.NotificationRequest;

public interface DlqService {
    /**
     * Routes a permanently failed notification request to the Dead Letter Queue (DLQ).
     * 
     * @param request The failed notification request payload
     * @param errorReason The reason/error message for the failure
     */
    void routeToDlq(NotificationRequest request, String errorReason);
}
