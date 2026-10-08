package com.axioapi;

import java.util.List;
import java.util.Map;

/** 402: not enough credits for this call. */
public class InsufficientCreditsException extends AxioApiException {
    private static final long serialVersionUID = 1L;

    public InsufficientCreditsException(String message, int status, String code, String requestId, Map<String, List<String>> fields) {
        super(message, status, code, requestId, fields);
    }
}
