package com.ketan.notification.service;

import com.ketan.notification.model.NotificationRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Profile("mock")
public class InMemoryDlqService implements DlqService {

    // Thread-safe list to hold DLQ messages for inspection
    private final List<NotificationRequest> dlqMessages = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void routeToDlq(NotificationRequest request, String errorReason) {
        System.err.println("MOCK CRITICAL: Routing transaction " + request.getTransactionId() 
            + " to IN-MEMORY DLQ. Reason: " + errorReason);
        dlqMessages.add(request);
    }

    /**
     * Helper to read the DLQ messages in memory for validation or testing.
     */
    public List<NotificationRequest> getDlqMessages() {
        return new ArrayList<>(dlqMessages);
    }
}
