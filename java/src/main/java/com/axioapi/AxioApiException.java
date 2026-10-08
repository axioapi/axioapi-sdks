package com.axioapi;

import java.util.List;
import java.util.Map;

/** Base class for API errors; carries the HTTP status, error code and request id for support. */
public class AxioApiException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int status;
    private final String code;
    private final String requestId;
    private final transient Map<String, List<String>> fields;

    public AxioApiException(String message, int status, String code, String requestId, Map<String, List<String>> fields) {
        super(describe(message, status, code, requestId));
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

    private static String describe(String message, int status, String code, String requestId) {
        if (status <= 0) {
            return message;
        }
        StringBuilder text = new StringBuilder(message).append(" (status ").append(status);
        if (code != null) {
            text.append(", code ").append(code);
        }
        if (requestId != null) {
            text.append(", request_id ").append(requestId);
        }
        return text.append(')').toString();
    }
}
