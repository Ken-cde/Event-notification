package com.ketan.notification.service;

public interface IdempotencyService {
    /**
     * Attempts to acquire an idempotency lease for the given transactionId.
     * 
     * @param transactionId Unique client-provided transaction identifier
     * @return true if this is a fresh transaction, false if it is a duplicate
     */
    boolean acquireLease(String transactionId);

    /**
     * Updates the status of the transaction to keep track of execution outcome.
     * 
     * @param transactionId Unique transaction identifier
     * @param status Transaction execution outcome status
     */
    void updateStatus(String transactionId, String status);
}
