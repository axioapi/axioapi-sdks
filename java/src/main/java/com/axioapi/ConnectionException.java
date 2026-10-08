package com.axioapi;

import java.util.Map;

/** The request never produced an HTTP response (DNS, TLS, timeout). */
public class ConnectionException extends AxioApiException {
    private static final long serialVersionUID = 1L;

    public ConnectionException(String message) {
        super(message, 0, null, null, Map.of());
    }
}
