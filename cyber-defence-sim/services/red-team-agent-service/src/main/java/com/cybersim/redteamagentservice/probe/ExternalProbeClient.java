package com.cybersim.redteamagentservice.probe;

import com.cybersim.shared.http.SafeHttpRequest;
import com.cybersim.shared.http.SafeHttpResponse;

/** A thin seam over the outbound HTTP call red-team checks make against external targets, so
 * {@code ExternalCheckStrategy} can be unit tested against a fake without any real network access. */
public interface ExternalProbeClient {
    SafeHttpResponse send(SafeHttpRequest request);
}
