package com.axioapi;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** Calls the operations of one API group, for example {@code client.group("seo").call("keywordMetrics", params)}. */
public final class OperationGroup {
    private final AxioApi client;
    private final String group;

    OperationGroup(AxioApi client, String group) {
        this.client = client;
        this.group = group;
    }

    public JsonNode call(String operation, Map<String, ?> params) {
        return client.call(keyOf(operation), params);
    }

    public JsonNode call(String operation) {
        return call(operation, Map.of());
    }

    public byte[] callBinary(String operation, Map<String, ?> params) {
        return client.callBinary(keyOf(operation), params);
    }

    private String keyOf(String operation) {
        String key = client.resolve(group, operation);
        if (key == null) {
            throw new IllegalArgumentException("AxioAPI has no operation '" + group + "." + operation + "'.");
        }
        return key;
    }
}
