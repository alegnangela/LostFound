package com.crescendo.lostfound.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Tags every request with a request id and writes one access-log line per request.
 *
 * <p>The id is taken from an incoming {@code X-Request-Id} header (so an id set by a gateway carries
 * through) or generated. It is put into the MDC, so every log line written while handling the
 * request carries it (see {@code logging.pattern.correlation}), and echoed in the response so a
 * client can quote it when reporting a problem. The filter runs before the security filter chain,
 * so rejected (401/403) requests are logged too. Actuator calls are not access-logged: health
 * probes would drown out real traffic.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    static final String REQUEST_ID_HEADER = "X-Request-Id";
    static final String REQUEST_ID_MDC_KEY = "requestId";

    /** A client-supplied id ends up in logs and headers, so anything but a short token is replaced. */
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        long startNanos = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            if (!request.getRequestURI().startsWith("/actuator")) {
                long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                log.info("{} {} -> {} ({} ms)",
                        request.getMethod(), request.getRequestURI(), response.getStatus(), durationMillis);
            }
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }

    private static String resolveRequestId(String incoming) {
        return incoming != null && VALID_REQUEST_ID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();
    }
}
