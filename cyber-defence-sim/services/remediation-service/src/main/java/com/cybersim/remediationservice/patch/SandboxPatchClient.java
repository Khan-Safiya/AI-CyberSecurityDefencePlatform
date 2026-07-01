package com.cybersim.remediationservice.patch;

import com.cybersim.remediationservice.model.RemediationType;

public interface SandboxPatchClient {
    PatchExecutionResult apply(RemediationType remediationType);

    PatchExecutionResult rollback(RemediationType remediationType);
}
