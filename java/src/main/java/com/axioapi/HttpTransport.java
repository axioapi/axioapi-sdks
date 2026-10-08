package com.axioapi;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** One HTTP round trip with java.net.http; HTTP error statuses are returned, not thrown. */
final class HttpTransport {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private final Duration timeout;

    HttpTransport(Duration timeout) {
        this.timeout = timeout;
    }

    /** @throws ConnectionException when no response arrived */
    HttpResult send(String method, String url, Map<String, String> headers, String payload) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout);
        headers.forEach(request::header);
        request.method(method, payload == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(payload));
        try {
            HttpResponse<byte[]> response = client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            return new HttpResult(response.statusCode(), response.headers(), response.body());
        } catch (IOException e) {
            throw new ConnectionException(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConnectionException("Interrupted");
        }
    }
}
