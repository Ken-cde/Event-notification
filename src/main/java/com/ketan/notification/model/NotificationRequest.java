package com.ketan.notification.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {

    @NotBlank(message = "Transaction ID is required for idempotency verification")
    private String transactionId;

    @NotBlank(message = "Recipient ID is required")
    private String recipientId;

    @NotBlank(message = "Channel type (EMAIL, SMS, PUSH) is required")
    @Pattern(regexp = "^(EMAIL|SMS|PUSH)$", message = "Channel must be EMAIL, SMS, or PUSH")
    private String channel;

    @NotBlank(message = "Destination contact address is required")
    private String destination;

    @NotBlank(message = "Message subject or title is required")
    private String subject;

    @NotBlank(message = "Message body content is required")
    private String message;
}
