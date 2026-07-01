package com.cybersim.verificationservice.workflow;

public record VerificationCheckResult(String status, String evidenceSummary) {
    public VerificationCheckResult withPendingSynchronization() {
        return new VerificationCheckResult(status,
                evidenceSummary + " Lifecycle synchronization is pending because a related service was unavailable.");
    }
}
