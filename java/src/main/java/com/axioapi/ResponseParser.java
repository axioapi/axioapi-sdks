package com.axioapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Turns HTTP responses into data or typed exceptions. */
final class ResponseParser {
    private static final ObjectMapper JSON = new ObjectMapper();

    private ResponseParser() {
    }

    /** Returns the `data` node, the whole envelope when raw, or byte[] for non-JSON bodies (files). */
    static Object parse(HttpResult response, boolean raw) {
        if (!response.isJson()) {
            return response.body;
        }
        if (response.body.length == 0) {
            return JSON.nullNode();
        }
        try {
            JsonNode envelope = JSON.readTree(response.body);
            if (raw || !envelope.isObject()) {
                return envelope;
            }
            JsonNode data = envelope.get("data");
            return data == null ? JSON.nullNode() : data;
        } catch (IOException e) {
            throw new AxioApiException("Invalid JSON response: " + e.getMessage(), response.status, null, null, Map.of());
        }
    }

    static AxioApiException error(HttpResult response) {
        JsonNode body = jsonOrNull(response.body);
        JsonNode detail = body != null && body.path("error").isObject() ? body.get("error") : JSON.createObjectNode();
        String message = detail.path("message").asText(body != null ? body.path("message").asText("HTTP " + response.status) : "HTTP " + response.status);
        String code = detail.path("code").isTextual() ? detail.get("code").asText() : null;
        String requestId = detail.path("request_id").asText(response.header("X-Request-Id"));
        Map<String, List<String>> fields = fields(detail.get("fields"));

        switch (response.status) {
            case 401: return new AuthenticationException(message, response.status, code, requestId, fields);
            case 402: return new InsufficientCreditsException(message, response.status, code, requestId, fields);
            case 404: return new NotFoundException(message, response.status, code, requestId, fields);
            case 422: return new ValidationException(message, response.status, code, requestId, fields);
            case 429: return new RateLimitException(message, response.status, code, requestId, fields, retryAfter(response));
            default: return new AxioApiException(message, response.status, code, requestId, fields);
        }
    }

    private static JsonNode jsonOrNull(byte[] content) {
        try {
            JsonNode node = JSON.readTree(content);
            return node != null && node.isObject() ? node : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static Map<String, List<String>> fields(JsonNode node) {
        Map<String, List<String>> fields = new LinkedHashMap<>();
        if (node != null && node.isObject()) {
            node.fields().forEachRemaining(entry -> fields.put(entry.getKey(), messages(entry.getValue())));
        }
        return fields;
    }

    private static List<String> messages(JsonNode value) {
        List<String> messages = new ArrayList<>();
        if (value.isArray()) {
            value.forEach(item -> messages.add(item.asText()));
        } else {
            messages.add(value.asText());
        }
        return messages;
    }

    private static Double retryAfter(HttpResult response) {
        String header = response.header("Retry-After");
        return header != null && header.matches("\\d+") ? Double.valueOf(header) : null;
    }
}
