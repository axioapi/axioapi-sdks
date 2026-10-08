package com.axioapi;

import java.util.List;
import java.util.Map;

/** 401: the API key is missing or invalid. */
public class AuthenticationException extends AxioApiException {
    private static final long serialVersionUID = 1L;

    public AuthenticationException(String message, int status, String code, String requestId, Map<String, List<String>> fields) {
        super(message, status, code, requestId, fields);
    }
}
