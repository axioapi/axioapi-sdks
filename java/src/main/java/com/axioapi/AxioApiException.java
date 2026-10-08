package com.axioapi;

import java.util.List;
import java.util.Map;

/** Base class for API errors; carries the HTTP status, error code and request id for support. */
public class AxioApiException extends RuntimeException {
    private final int status;
    private final String code;
    private final String requestId;
    private final Map<String, List<String>> fields;

    public AxioApiException(String message, int status, String code, String requestId, Map<String, List<String>> fields) {
        super(message + (status > 0 ? " (status " + status + (code != null ? ", code " + code : "") + (requestId != null ? ", request_id " + requestId : "") + ")" : ""));
        this.status = status;
        this.code = code;
        this.requestId = requestId;
        this.fields = fields;
    }

    /** HTTP status, or 0 when there was no response. */
    public int getStatus() { return status; }
    public String getCode() { return code; }
    public String getRequestId() { return requestId; }
    /** Per-field messages for 422 responses. */
    public Map<String, List<String>> getFields() { return fields; }

    /** 401: the API key is missing or invalid. */
    public static final class AuthenticationException extends AxioApiException {
        public AuthenticationException(String m, int s, String c, String r, Map<String, List<String>> f) { super(m, s, c, r, f); }
    }

    /** 402: not enough credits. */
    public static final class InsufficientCreditsException extends AxioApiException {
        public InsufficientCreditsException(String m, int s, String c, String r, Map<String, List<String>> f) { super(m, s, c, r, f); }
    }

    /** 404: the resource does not exist, expired or is not yours. */
    public static final class NotFoundException extends AxioApiException {
        public NotFoundException(String m, int s, String c, String r, Map<String, List<String>> f) { super(m, s, c, r, f); }
    }

    /** 422: invalid parameters; see {@link #getFields()}. */
    public static final class ValidationException extends AxioApiException {
        public ValidationException(String m, int s, String c, String r, Map<String, List<String>> f) { super(m, s, c, r, f); }
    }

    /** 429: request limit reached; {@link #getRetryAfter()} is in seconds when the server sent it. */
    public static final class RateLimitException extends AxioApiException {
        private final Double retryAfter;

        public RateLimitException(String m, int s, String c, String r, Map<String, List<String>> f, Double retryAfter) {
            super(m, s, c, r, f);
            this.retryAfter = retryAfter;
        }

        public Double getRetryAfter() { return retryAfter; }
    }

    /** The request never produced an HTTP response (DNS, TLS, timeout). */
    public static final class ConnectionException extends AxioApiException {
        public ConnectionException(String message) { super(message, 0, null, null, Map.of()); }
    }
}
