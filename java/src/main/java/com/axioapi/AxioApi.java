package com.axioapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongConsumer;

/**
 * Client for https://axioapi.com. Create an API key in your account and pass it here or set AXIOAPI_KEY.
 *
 * <pre>{@code
 * AxioApi client = AxioApi.builder("ak_...").build();
 * JsonNode data = client.group("seo").call("keywordMetrics", Map.of("keywords", List.of("api gateway"), "country", "us"));
 * }</pre>
 */
public final class AxioApi {
    public static final String VERSION = "1.0.0";
    private static final String DEFAULT_BASE_URL = "https://axioapi.com";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String apiKey;
    private final String baseUrl;
    private final String userAgent;
    private final int maxRetries;
    private final Duration timeout;
    private final HttpClient http;
    private final LongConsumer sleeper;
    private final Map<String, Operation> operations = new LinkedHashMap<>();
    private final Map<String, String> index = new HashMap<>();

    /** One API operation as described in the registry. */
    public static final class Operation {
        public final String method;
        public final String path;
        public final String summary;
        public final Double cost;
        public final List<String> pathParams;
        public final List<String> query;
        public final List<String> body;
        public final boolean binary;

        Operation(JsonNode n) {
            this.method = n.get("method").asText();
            this.path = n.get("path").asText();
            this.summary = n.path("summary").asText("");
            this.cost = n.get("cost").isNumber() ? n.get("cost").asDouble() : null;
            this.pathParams = strings(n.get("path_params"));
            this.query = strings(n.get("query"));
            this.body = strings(n.get("body"));
            this.binary = n.path("binary").asBoolean(false);
        }

        private static List<String> strings(JsonNode array) {
            List<String> out = new ArrayList<>();
            array.forEach(item -> out.add(item.asText()));
            return Collections.unmodifiableList(out);
        }
    }

    /** Builder for {@link AxioApi}. */
    public static final class Builder {
        private final String apiKey;
        private String baseUrl;
        private Duration timeout = Duration.ofSeconds(30);
        private int maxRetries = 2;
        private String userAgent;
        private LongConsumer sleeper = millis -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        private Builder(String apiKey) {
            this.apiKey = apiKey;
        }

        public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }
        public Builder timeout(Duration timeout) { this.timeout = timeout; return this; }
        /** Retries for 429 and, on GET/DELETE, 502/503/504 and network errors. Default 2. */
        public Builder maxRetries(int maxRetries) { this.maxRetries = Math.max(0, maxRetries); return this; }
        public Builder userAgent(String userAgent) { this.userAgent = userAgent; return this; }
        /** Replaces the backoff sleep (milliseconds); used by tests. */
        public Builder sleeper(LongConsumer sleeper) { this.sleeper = sleeper; return this; }
        public AxioApi build() { return new AxioApi(this); }
    }

    /** @param apiKey null or empty falls back to the AXIOAPI_KEY environment variable. */
    public static Builder builder(String apiKey) {
        return new Builder(apiKey);
    }

    private AxioApi(Builder b) {
        String key = b.apiKey != null && !b.apiKey.isEmpty() ? b.apiKey : System.getenv("AXIOAPI_KEY");
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Pass an API key or set the AXIOAPI_KEY environment variable.");
        }
        this.apiKey = key;
        String base = b.baseUrl != null ? b.baseUrl : System.getenv("AXIOAPI_BASE_URL");
        this.baseUrl = (base != null && !base.isEmpty() ? base : DEFAULT_BASE_URL).replaceAll("/+$", "");
        this.userAgent = b.userAgent != null ? b.userAgent : "axioapi-java/" + VERSION;
        this.maxRetries = b.maxRetries;
        this.timeout = b.timeout;
        this.sleeper = b.sleeper;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        try (InputStream in = AxioApi.class.getResourceAsStream("/operations.json")) {
            if (in == null) {
                throw new IllegalStateException("operations.json resource is missing.");
            }
            JsonNode ops = JSON.readTree(in).get("operations");
            Iterator<Map.Entry<String, JsonNode>> it = ops.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                operations.put(e.getKey(), new Operation(e.getValue()));
                int dot = e.getKey().indexOf('.');
                index.put(normalize(e.getKey().substring(0, dot)) + "." + normalize(e.getKey().substring(dot + 1)), e.getKey());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read operations.json", e);
        }
    }

    /** Every operation: method, path, parameters and credit cost. */
    public Map<String, Operation> operations() {
        return Collections.unmodifiableMap(operations);
    }

    /** Capability key for a group and operation name (camelCase, snake_case or kebab-case), or null. */
    public String resolve(String group, String name) {
        return index.get(normalize(group) + "." + normalize(name));
    }

    /** Scopes calls to one API group, for example {@code client.group("seo").call("keywordMetrics", params)}. */
    public Group group(String name) {
        return new Group(name);
    }

    /** A set of operations that share a prefix. */
    public final class Group {
        private final String name;

        private Group(String name) {
            this.name = name;
        }

        private String key(String op) {
            String key = resolve(name, op);
            if (key == null) {
                throw new IllegalArgumentException("AxioAPI has no operation '" + name + "." + op + "'.");
            }
            return key;
        }

        public JsonNode call(String op, Map<String, ?> params) {
            return AxioApi.this.call(key(op), params);
        }

        public JsonNode call(String op) {
            return call(op, Map.of());
        }

        public byte[] callBinary(String op, Map<String, ?> params) {
            return AxioApi.this.callBinary(key(op), params);
        }
    }

    private static final class Prepared {
        Operation op;
        String path;
        Map<String, Object> query = new LinkedHashMap<>();
        Map<String, Object> body;
    }

    private Prepared prepare(String operation, Map<String, ?> params) {
        String key = operations.containsKey(operation) ? operation : null;
        if (key == null && operation.indexOf('.') > 0) {
            int dot = operation.indexOf('.');
            key = resolve(operation.substring(0, dot), operation.substring(dot + 1));
        }
        if (key == null) {
            throw new IllegalArgumentException("Unknown operation '" + operation + "'. See operations().");
        }
        Prepared p = new Prepared();
        p.op = operations.get(key);
        Map<String, Object> rest = new LinkedHashMap<>(params == null ? Map.of() : params);
        String path = p.op.path;
        for (String name : p.op.pathParams) {
            Object value = rest.remove(name);
            if (value == null) {
                throw new IllegalArgumentException("Missing path parameter '" + name + "' for " + key);
            }
            path = path.replace("{" + name + "}", URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8).replace("+", "%20"));
        }
        p.path = path;
        boolean hasBody = !List.of("GET", "DELETE", "HEAD").contains(p.op.method);
        Map<String, Object> body = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : rest.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            if (hasBody && (p.op.body.contains(e.getKey()) || !p.op.query.contains(e.getKey()))) {
                body.put(e.getKey(), e.getValue());
            } else {
                p.query.put(e.getKey(), e.getValue());
            }
        }
        p.body = hasBody && !body.isEmpty() ? body : null;
        return p;
    }

    /** Call an operation by capability key (for example "seo.keyword-metrics"). Returns the {@code data} node. */
    public JsonNode call(String operation, Map<String, ?> params) {
        Prepared p = prepare(operation, params);
        if (p.op.binary) {
            throw new IllegalStateException(operation + " returns a file; use callBinary.");
        }
        return (JsonNode) send(p.op.method, p.path, p.query, p.body, false);
    }

    public JsonNode call(String operation) {
        return call(operation, Map.of());
    }

    /** Call an operation that returns a file (image or audio). */
    public byte[] callBinary(String operation, Map<String, ?> params) {
        Prepared p = prepare(operation, params);
        Object result = send(p.op.method, p.path, p.query, p.body, false);
        if (!(result instanceof byte[])) {
            throw new IllegalStateException(operation + " did not return a file.");
        }
        return (byte[]) result;
    }

    /** Send a request. Returns {@code data}, or the whole envelope when {@code raw} is true. */
    public JsonNode request(String method, String path, Map<String, ?> query, Object body, boolean raw) {
        Object result = send(method, path, query == null ? Map.of() : query, body, raw);
        if (result instanceof byte[]) {
            throw new IllegalStateException("The response is a file; use callBinary.");
        }
        return (JsonNode) result;
    }

    private Object send(String method, String path, Map<String, ?> query, Object body, boolean raw) {
        String m = method.toUpperCase();
        String url = baseUrl + (path.startsWith("/") ? path : "/" + path);
        if (!query.isEmpty()) {
            url += "?" + buildQuery(query);
        }
        String payload = null;
        if (body != null) {
            try {
                payload = JSON.writeValueAsString(body);
            } catch (IOException e) {
                throw new IllegalArgumentException("Cannot encode body", e);
            }
        }
        boolean idempotent = m.equals("GET") || m.equals("HEAD") || m.equals("DELETE");

        for (int attempt = 0; ; attempt++) {
            HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent);
            if (payload != null) {
                rb.header("Content-Type", "application/json");
                rb.method(m, HttpRequest.BodyPublishers.ofString(payload));
            } else {
                rb.method(m, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<byte[]> res;
            try {
                res = http.send(rb.build(), HttpResponse.BodyHandlers.ofByteArray());
            } catch (IOException e) {
                if (idempotent && attempt < maxRetries) {
                    sleeper.accept(backoff(null, attempt));
                    continue;
                }
                throw new AxioApiException.ConnectionException("Could not reach " + baseUrl + ": " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AxioApiException.ConnectionException("Interrupted");
            }
            int status = res.statusCode();
            if (status >= 200 && status < 300) {
                return parse(res, raw);
            }
            boolean retryable = status == 429 || (idempotent && (status == 502 || status == 503 || status == 504));
            if (retryable && attempt < maxRetries) {
                sleeper.accept(backoff(res.headers().firstValue("Retry-After").orElse(null), attempt));
                continue;
            }
            throw buildError(res);
        }
    }

    private Object parse(HttpResponse<byte[]> res, boolean raw) {
        String type = res.headers().firstValue("Content-Type").orElse("");
        if (!type.contains("json")) {
            return res.body();
        }
        if (res.body().length == 0) {
            return JSON.nullNode();
        }
        try {
            JsonNode envelope = JSON.readTree(res.body());
            if (raw || !envelope.isObject()) {
                return envelope;
            }
            JsonNode data = envelope.get("data");
            return data == null ? JSON.nullNode() : data;
        } catch (IOException e) {
            throw new AxioApiException("Invalid JSON response: " + e.getMessage(), res.statusCode(), null, null, Map.of());
        }
    }

    private AxioApiException buildError(HttpResponse<byte[]> res) {
        int status = res.statusCode();
        String message = "HTTP " + status;
        String code = null;
        String requestId = res.headers().firstValue("X-Request-Id").orElse(null);
        Map<String, List<String>> fields = new LinkedHashMap<>();
        try {
            JsonNode body = JSON.readTree(res.body());
            if (body != null && body.isObject()) {
                if (body.path("message").isTextual()) {
                    message = body.get("message").asText();
                }
                JsonNode err = body.get("error");
                if (err != null && err.isObject()) {
                    message = err.path("message").asText(message);
                    code = err.path("code").isTextual() ? err.get("code").asText() : null;
                    requestId = err.path("request_id").asText(requestId);
                    JsonNode f = err.get("fields");
                    if (f != null && f.isObject()) {
                        f.fields().forEachRemaining(e -> {
                            List<String> list = new ArrayList<>();
                            if (e.getValue().isArray()) {
                                e.getValue().forEach(x -> list.add(x.asText()));
                            } else {
                                list.add(e.getValue().asText());
                            }
                            fields.put(e.getKey(), list);
                        });
                    }
                }
            }
        } catch (IOException ignored) {
            // not JSON
        }
        switch (status) {
            case 401: return new AxioApiException.AuthenticationException(message, status, code, requestId, fields);
            case 402: return new AxioApiException.InsufficientCreditsException(message, status, code, requestId, fields);
            case 404: return new AxioApiException.NotFoundException(message, status, code, requestId, fields);
            case 422: return new AxioApiException.ValidationException(message, status, code, requestId, fields);
            case 429: {
                Double retryAfter = null;
                String header = res.headers().firstValue("Retry-After").orElse(null);
                if (header != null && header.matches("\\d+")) {
                    retryAfter = Double.valueOf(header);
                }
                return new AxioApiException.RateLimitException(message, status, code, requestId, fields, retryAfter);
            }
            default: return new AxioApiException(message, status, code, requestId, fields);
        }
    }

    private static String buildQuery(Map<String, ?> query) {
        List<String> pairs = new ArrayList<>();
        for (Map.Entry<String, ?> e : query.entrySet()) {
            Object v = e.getValue();
            if (v == null) {
                continue;
            }
            if (v instanceof Iterable) {
                for (Object item : (Iterable<?>) v) {
                    pairs.add(enc(e.getKey() + "[]") + "=" + enc(String.valueOf(item)));
                }
            } else if (v instanceof Object[]) {
                for (Object item : (Object[]) v) {
                    pairs.add(enc(e.getKey() + "[]") + "=" + enc(String.valueOf(item)));
                }
            } else {
                pairs.add(enc(e.getKey()) + "=" + enc(String.valueOf(v)));
            }
        }
        return String.join("&", pairs);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static long backoff(String retryAfter, int attempt) {
        if (retryAfter != null && retryAfter.matches("\\d+(\\.\\d+)?")) {
            return (long) (Math.min(Double.parseDouble(retryAfter), 30.0) * 1000);
        }
        return (long) (Math.min(0.5 * Math.pow(2, attempt), 8.0) * 1000);
    }

    private static String normalize(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
