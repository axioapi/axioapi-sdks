package com.axioapi;

import java.util.Map;

/** An operation with its parameters placed in path, query and body. */
final class PreparedRequest {
    final String method;
    final String path;
    final Map<String, Object> query;
    final Map<String, Object> body;

    PreparedRequest(String method, String path, Map<String, Object> query, Map<String, Object> body) {
        this.method = method;
        this.path = path;
        this.query = query;
        this.body = body;
    }
}
