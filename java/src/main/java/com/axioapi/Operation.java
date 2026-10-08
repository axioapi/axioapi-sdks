package com.axioapi;

import java.util.List;

/** One API operation as described in the registry. */
public final class Operation {
    private static final List<String> BODYLESS_METHODS = List.of("GET", "DELETE", "HEAD");

    public final String key;
    public final String method;
    public final String path;
    public final String summary;
    public final Double cost;
    public final List<String> pathParams;
    public final List<String> query;
    public final List<String> body;
    public final boolean binary;

    Operation(String key, String method, String path, String summary, Double cost,
              List<String> pathParams, List<String> query, List<String> body, boolean binary) {
        this.key = key;
        this.method = method;
        this.path = path;
        this.summary = summary;
        this.cost = cost;
        this.pathParams = pathParams;
        this.query = query;
        this.body = body;
        this.binary = binary;
    }

    public boolean hasBody() {
        return !BODYLESS_METHODS.contains(method);
    }
}
