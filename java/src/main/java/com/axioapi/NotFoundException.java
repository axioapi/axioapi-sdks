package com.axioapi;

import java.util.List;
import java.util.Map;

/** 404: the resource does not exist, expired or is not yours. */
public class NotFoundException extends AxioApiException {
    private static final long serialVersionUID = 1L;

    public NotFoundException(String message, int status, String code, String requestId, Map<String, List<String>> fields) {
        super(message, status, code, requestId, fields);
    }
}
