package com.cybersim.shared.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public final class CorrelationIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String correlationId = CorrelationIds.acceptOrCreate(request.getHeader(CorrelationIds.HEADER_NAME));
        String previousCorrelationId = MDC.get(CorrelationIds.MDC_KEY);

        request.setAttribute(CorrelationIds.REQUEST_ATTRIBUTE, correlationId);
        response.setHeader(CorrelationIds.HEADER_NAME, correlationId);
        MDC.put(CorrelationIds.MDC_KEY, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (previousCorrelationId == null) {
                MDC.remove(CorrelationIds.MDC_KEY);
            } else {
                MDC.put(CorrelationIds.MDC_KEY, previousCorrelationId);
            }
        }
    }
}
