package com.axioapi;

import java.net.http.HttpHeaders;

/** Status, headers and body of one HTTP response. */
final class HttpResult {
    final int status;
    final HttpHeaders headers;
    final byte[] body;

    HttpResult(int status, HttpHeaders headers, byte[] body) {
        this.status = status;
        this.headers = headers;
        this.body = body;
    }

    String header(String name) {
        return headers.firstValue(name).orElse(null);
    }

    boolean isSuccess() {
        return status >= 200 && status < 300;
    }

    boolean isJson() {
        String type = header("Content-Type");
        return type != null && type.contains("json");
    }
}
