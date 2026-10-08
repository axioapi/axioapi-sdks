package com.axioapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongConsumer;

/**
 * Client for https://axioapi.com. Pass an API key or set AXIOAPI_KEY.
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
    private final Registry registry = Registry.load();
    private final RetryPolicy retry;
    private final HttpTransport transport;

    private AxioApi(Builder builder) {
        this.apiKey = firstNonEmpty(builder.apiKey, System.getenv("AXIOAPI_KEY"));
        if (apiKey == null) {
            throw new IllegalArgumentException("Pass an API key or set the AXIOAPI_KEY environment variable.");
        }
        String base = firstNonEmpty(builder.baseUrl, System.getenv("AXIOAPI_BASE_URL"));
        this.baseUrl = (base != null ? base : DEFAULT_BASE_URL).replaceAll("/+$", "");
        this.userAgent = builder.userAgent != null ? builder.userAgent : "axioapi-java/" + VERSION;
        this.retry = new RetryPolicy(builder.maxRetries, builder.sleeper);
        this.transport = new HttpTransport(builder.timeout);
    }

    /** @param apiKey null or empty falls back to the AXIOAPI_KEY environment variable. */
    public static Builder builder(String apiKey) {
        return new Builder(apiKey);
    }

    /** Every operation: method, path, parameters and credit cost. */
    public Map<String, Operation> operations() {
        return registry.all();
    }

    /** Capability key for a group and operation name (camelCase, snake_case or kebab-case), or null. */
    public String resolve(String group, String name) {
        return registry.resolve(group, name);
    }

    public OperationGroup group(String name) {
        return new OperationGroup(this, name);
    }

    /** Calls an operation by capability key (for example "seo.keyword-metrics") and returns the data node. */
    public JsonNode call(String operation, Map<String, ?> params) {
        Operation found = require(operation);
        if (found.binary) {
            throw new IllegalStateException(operation + " returns a file; use callBinary.");
        }
        return (JsonNode) execute(found, params);
    }

    public JsonNode call(String operation) {
        return call(operation, Map.of());
    }

    /** Calls an operation that returns a file (image or audio). */
    public byte[] callBinary(String operation, Map<String, ?> params) {
        Object result = execute(require(operation), params);
        if (!(result instanceof byte[])) {
            throw new IllegalStateException(operation + " did not return a file.");
        }
        return (byte[]) result;
    }

    /** Sends a request with retries; returns the data node, or the whole envelope when raw is true. */
    public JsonNode request(String method, String path, Map<String, ?> query, Object body, boolean raw) {
        Object result = send(method, path, query == null ? Map.of() : query, body, raw);
        if (result instanceof byte[]) {
            throw new IllegalStateException("The response is a file; use callBinary.");
        }
        return (JsonNode) result;
    }

    private Operation require(String operation) {
        Operation found = registry.find(operation);
        if (found == null) {
            throw new IllegalArgumentException("Unknown operation '" + operation + "'. See operations().");
        }
        return found;
    }

    private Object execute(Operation operation, Map<String, ?> params) {
        PreparedRequest prepared = RequestBuilder.build(operation, params);
        return send(prepared.method, prepared.path, prepared.query, prepared.body, false);
    }

    private Object send(String method, String path, Map<String, ?> query, Object body, boolean raw) {
        String verb = method.toUpperCase();
        String url = url(path, query);
        String payload = encode(body);
        Map<String, String> headers = headers(payload != null);

        for (int attempt = 0; ; attempt++) {
            HttpResult response;
            try {
                response = transport.send(verb, url, headers, payload);
            } catch (ConnectionException failure) {
                if (retry.shouldRetryConnection(verb, attempt)) {
                    retry.await(attempt, null);
                    continue;
                }
                throw new ConnectionException("Could not reach " + baseUrl + ": " + failure.getMessage());
            }
            if (response.isSuccess()) {
                return ResponseParser.parse(response, raw);
            }
            if (!retry.shouldRetryStatus(response.status, verb, attempt)) {
                throw ResponseParser.error(response);
            }
            retry.await(attempt, response.header("Retry-After"));
        }
    }

    private String url(String path, Map<String, ?> query) {
        String url = baseUrl + (path.startsWith("/") ? path : "/" + path);
        return query.isEmpty() ? url : url + "?" + RequestBuilder.encodeQuery(query);
    }

    private Map<String, String> headers(boolean hasBody) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("Accept", "application/json");
        headers.put("User-Agent", userAgent);
        if (hasBody) {
            headers.put("Content-Type", "application/json");
        }
        return headers;
    }

    private static String encode(Object body) {
        if (body == null) {
            return null;
        }
        try {
            return JSON.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot encode body", e);
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    /** Builder for {@link AxioApi}. */
    public static final class Builder {
        private final String apiKey;
        private String baseUrl;
        private Duration timeout = Duration.ofSeconds(30);
        private int maxRetries = 2;
        private String userAgent;
        private LongConsumer sleeper = AxioApi::sleep;

        private Builder(String apiKey) {
            this.apiKey = apiKey;
        }

        public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }

        public Builder timeout(Duration timeout) { this.timeout = timeout; return this; }

        /** Retries for 429 and, on GET/DELETE, 502/503/504 and network errors. Default 2. */
        public Builder maxRetries(int maxRetries) { this.maxRetries = maxRetries; return this; }

        public Builder userAgent(String userAgent) { this.userAgent = userAgent; return this; }

        /** Replaces the backoff sleep (milliseconds); used by tests. */
        public Builder sleeper(LongConsumer sleeper) { this.sleeper = sleeper; return this; }

        public AxioApi build() { return new AxioApi(this); }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
