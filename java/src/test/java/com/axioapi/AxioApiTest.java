package com.axioapi;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AxioApiTest {
    private static final String URL = System.getenv("AXIOAPI_MOCK_URL") != null ? System.getenv("AXIOAPI_MOCK_URL") : "http://127.0.0.1:8765";
    private static final HttpClient PLAIN = HttpClient.newHttpClient();

    private static String get(String path) throws IOException, InterruptedException {
        return PLAIN.send(HttpRequest.newBuilder(URI.create(URL + path)).build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    private static AxioApi client(String key, int retries) {
        return AxioApi.builder(key).baseUrl(URL).maxRetries(retries).sleeper(ms -> { }).build();
    }

    private static AxioApi client() {
        return client("test_key", 2);
    }

    @BeforeEach
    void reset() throws Exception {
        get("/__reset");
    }

    @Test
    void groupCallSendsJsonBodyAndUnwrapsData() {
        Map<String, Object> params = new HashMap<>();
        params.put("keywords", List.of("api gateway", "proxy scraper"));
        params.put("country", "us");
        JsonNode data = client().group("seo").call("keywordMetrics", params);
        assertEquals("POST", data.get("method").asText());
        assertEquals("/api/v1/seo/keywords/metrics", data.get("path").asText());
        assertEquals("us", data.get("body").get("country").asText());
        assertEquals(2, data.get("body").get("keywords").size());
        assertEquals("application/json", data.get("content_type").asText());
        assertTrue(data.get("user_agent").asText().startsWith("axioapi-java/"));
    }

    @Test
    void snakeAndKebabNamesResolve() {
        AxioApi c = client();
        assertEquals("/api/v1/seo/keywords/metrics", c.group("seo").call("keyword_metrics", Map.of("keywords", List.of("a"))).get("path").asText());
        assertEquals("/api/v1/seo/keywords/metrics", c.call("seo.keyword-metrics", Map.of("keywords", List.of("a"))).get("path").asText());
    }

    @Test
    void pathParamsEncodedAndQuerySeparated() {
        AxioApi c = client();
        assertEquals("/api/v1/seo/domains/exa%20mple.com/backlinks", c.call("seo.backlinks-summary", Map.of("domain", "exa mple.com")).get("path").asText());
        Map<String, Object> params = new HashMap<>();
        params.put("q", "laravel api");
        params.put("country", "us");
        params.put("lang", "en");
        params.put("skipped", null);
        JsonNode query = c.group("seo").call("keywordSuggestions", params).get("query");
        assertEquals("laravel api", query.get("q").get(0).asText());
        assertEquals(3, query.size());
    }

    @Test
    void missingPathParamAndUnknownOperation() {
        AxioApi c = client();
        assertThrows(IllegalArgumentException.class, () -> c.call("seo.backlinks-summary"));
        assertThrows(IllegalArgumentException.class, () -> c.call("nope.nothing"));
        assertThrows(IllegalArgumentException.class, () -> c.group("seo").call("doesNotExist"));
    }

    @Test
    void everyRegistryOperationIsReachable() {
        AxioApi c = client();
        assertTrue(c.operations().size() > 100);
        for (String key : c.operations().keySet()) {
            int dot = key.indexOf('.');
            assertEquals(key, c.resolve(key.substring(0, dot), key.substring(dot + 1)));
        }
    }

    @Test
    void rawEnvelope() {
        JsonNode env = client().request("GET", "/api/v1/account/limits", null, null, true);
        assertEquals("success", env.get("status").asText());
        assertEquals("req_test_1", env.get("request").get("id").asText());
    }

    @Test
    void binaryDownloadReturnsBytes() {
        byte[] bytes = client().callBinary("ai-image.artifact", Map.of("job", "abc"));
        assertEquals((byte) 0x89, bytes[0]);
        assertThrows(IllegalStateException.class, () -> client().call("ai-image.artifact", Map.of("job", "abc")));
    }

    @Test
    void errorsMapToClasses() {
        AxioApiException.AuthenticationException auth = assertThrows(AxioApiException.AuthenticationException.class, () -> client("wrong", 2).group("account").call("limits"));
        assertEquals(401, auth.getStatus());
        assertEquals("req_test_1", auth.getRequestId());
        assertThrows(AxioApiException.InsufficientCreditsException.class, () -> client("nocredit", 2).group("account").call("limits"));
        assertThrows(AxioApiException.NotFoundException.class, () -> client().request("GET", "/api/v1/temp-mail/inboxes/missing", null, null, false));
        AxioApiException.ValidationException validation = assertThrows(AxioApiException.ValidationException.class, () -> client().group("seo").call("onPageAudit"));
        assertEquals(List.of("The url field is required."), validation.getFields().get("url"));
        AxioApiException other = assertThrows(AxioApiException.class, () -> client().request("GET", "/api/v1/boom", null, null, false));
        assertEquals(500, other.getStatus());
        assertFalse(other instanceof AxioApiException.AuthenticationException);
    }

    @Test
    void retriesIdempotent503ThenSucceeds() {
        assertEquals(3, client().request("GET", "/api/v1/flaky", null, null, false).get("attempts").asInt());
    }

    @Test
    void retries429GivesUpWithRetryAfterAndPost503NotRetried() throws Exception {
        assertEquals(2, client().request("GET", "/api/v1/ratelimited", null, null, false).get("attempts").asInt());
        AxioApiException.RateLimitException rate = assertThrows(AxioApiException.RateLimitException.class, () -> client("test_key", 1).request("POST", "/api/v1/always429", null, Map.of(), false));
        assertEquals(7.0, rate.getRetryAfter());
        assertTrue(get("/__hits").contains("\"always429\": 2") || get("/__hits").contains("\"always429\":2"));
        assertThrows(AxioApiException.class, () -> client().request("POST", "/api/v1/post503", null, Map.of(), false));
        String hits = get("/__hits");
        assertTrue(hits.contains("\"post503\": 1") || hits.contains("\"post503\":1"));
    }

    @Test
    void connectionError() {
        AxioApi c = AxioApi.builder("k").baseUrl("http://127.0.0.1:1").maxRetries(0).timeout(Duration.ofSeconds(2)).build();
        assertThrows(AxioApiException.ConnectionException.class, () -> c.group("account").call("limits"));
        assertNotNull(c);
    }
}
