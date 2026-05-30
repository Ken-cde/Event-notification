package com.ketan.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ketan.notification.model.NotificationRequest;
import com.ketan.notification.producer.NotificationProducer;
import com.ketan.notification.repository.NotificationLogRepository;
import com.ketan.notification.service.IdempotencyService;
import com.ketan.notification.service.RateLimiterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
public class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IdempotencyService idempotencyService;

    @MockBean
    private RateLimiterService rateLimiterService;

    @MockBean
    private NotificationProducer notificationProducer;

    @MockBean
    private NotificationLogRepository auditLogRepository;

    @Test
    public void testSendNotification_SuccessfulIngestion_ShouldReturn202() throws Exception {
        // Arrange
        NotificationRequest request = NotificationRequest.builder()
            .transactionId("tx_111")
            .recipientId("user_111")
            .channel("EMAIL")
            .destination("test@gmail.com")
            .subject("Alert")
            .message("Security check")
            .build();

        when(idempotencyService.acquireLease(anyString())).thenReturn(true);
        when(rateLimiterService.isAllowed(anyString())).thenReturn(true);

        // Act & Assert
        mockMvc.perform(post("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("INGESTED"))
                .andExpect(jsonPath("$.transactionId").value("tx_111"))
                .andExpect(jsonPath("$.message").value("Notification accepted and queued successfully."));

        verify(notificationProducer).publishToQueue(any());
        verify(auditLogRepository).save(any());
    }

    @Test
    public void testSendNotification_DuplicateTransaction_ShouldReturn200() throws Exception {
        // Arrange
        NotificationRequest request = NotificationRequest.builder()
            .transactionId("tx_dup")
            .recipientId("user_111")
            .channel("EMAIL")
            .destination("test@gmail.com")
            .subject("Alert")
            .message("Security check")
            .build();

        when(idempotencyService.acquireLease(eq("tx_dup"))).thenReturn(false);
        when(auditLogRepository.findByTransactionId(eq("tx_dup"))).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(post("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEDUPLICATED"))
                .andExpect(jsonPath("$.message").value("Duplicate transaction detected and skipped for delivery safety."));

        verify(notificationProducer, never()).publishToQueue(any());
    }

    @Test
    public void testSendNotification_RateLimitedRecipient_ShouldReturn429() throws Exception {
        // Arrange
        NotificationRequest request = NotificationRequest.builder()
            .transactionId("tx_limited")
            .recipientId("user_spam")
            .channel("EMAIL")
            .destination("test@gmail.com")
            .subject("Alert")
            .message("Security check")
            .build();

        when(idempotencyService.acquireLease(eq("tx_limited"))).thenReturn(true);
        when(rateLimiterService.isAllowed(eq("user_spam"))).thenReturn(false);

        // Act & Assert
        mockMvc.perform(post("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.message").value("Rate limit exceeded. Maximum allowed messages reached for recipient."));

        verify(idempotencyService).updateStatus(eq("tx_limited"), eq("RATE_LIMITED"));
        verify(notificationProducer, never()).publishToQueue(any());
    }
}
