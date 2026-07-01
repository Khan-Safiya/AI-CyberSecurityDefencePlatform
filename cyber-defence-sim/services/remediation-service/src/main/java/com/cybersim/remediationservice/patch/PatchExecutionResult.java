package com.cybersim.remediationservice.patch;

public record PatchExecutionResult(boolean successful, String summary) {
    public static PatchExecutionResult success(String summary) {
        return new PatchExecutionResult(true, summary);
    }

    public static PatchExecutionResult failure(String summary) {
        return new PatchExecutionResult(false, summary);
    }
}
