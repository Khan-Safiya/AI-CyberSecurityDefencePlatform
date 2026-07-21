package com.cybersim.redteamagentservice.probe;

import com.cybersim.shared.http.SafeHttpRequest;
import com.cybersim.shared.http.SafeHttpResponse;
import com.cybersim.shared.http.SafeOutboundHttpClient;
import org.springframework.stereotype.Component;

@Component
class SafeOutboundExternalProbeClient implements ExternalProbeClient {
    private final SafeOutboundHttpClient httpClient = SafeOutboundHttpClient.create();

    @Override
    public SafeHttpResponse send(SafeHttpRequest request) {
        return httpClient.send(request);
    }
}
