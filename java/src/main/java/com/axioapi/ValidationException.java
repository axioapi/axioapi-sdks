package com.axioapi;

import java.util.List;
import java.util.Map;

/** 422: invalid parameters; see {@link #getFields()}. */
public class ValidationException extends AxioApiException {
    private static final long serialVersionUID = 1L;

    public ValidationException(String message, int status, String code, String requestId, Map<String, List<String>> fields) {
        super(message, status, code, requestId, fields);
    }
}
