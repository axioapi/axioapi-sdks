package com.axioapi;

import java.util.List;
import java.util.Map;

/** 429: request limit reached; {@link #getRetryAfter()} is in seconds when the server sent it. */
public class RateLimitException extends AxioApiException {
    private static final long serialVersionUID = 1L;

    private final Double retryAfter;

    public RateLimitException(String message, int status, String code, String requestId, Map<String, List<String>> fields, Double retryAfter) {
        super(message, status, code, requestId, fields);
        this.retryAfter = retryAfter;
    }

    public Double getRetryAfter() { return retryAfter; }
}
