package com.cybersim.remediationservice.model;

public enum RemediationType {
    AUTH_REQUIRED("auth-required"),
    OBJECT_AUTHORIZATION("object-authorization"),
    RATE_LIMIT("rate-limit"),
    DISABLE_DEBUG_ENDPOINT("disable-debug-endpoint"),
    INPUT_VALIDATION("input-validation"),
    UPDATE_DEPENDENCY_METADATA("update-dependency-metadata");

    private final String patchName;

    RemediationType(String patchName) {
        this.patchName = patchName;
    }

    public String patchName() {
        return patchName;
    }
}
