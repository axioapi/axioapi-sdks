# axioapi for Java

Java client for the [AxioAPI](https://axioapi.com) REST API: temp mail API, receive SMS and OTP API, email validation API, proxy API, SEO API (keyword data and backlink API) and social scraper APIs. One API key, pay per request. Java 11+, depends only on Jackson.

```xml
<dependency>
  <groupId>com.axioapi</groupId>
  <artifactId>axioapi</artifactId>
  <version>1.0.0</version>
</dependency>
```

```java
import com.axioapi.AxioApi;
import com.axioapi.AxioApiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

AxioApi client = AxioApi.builder(null).build();   // null reads AXIOAPI_KEY

// Keyword data API: volume, CPC and competition for up to 10 keywords per request
JsonNode rows = client.group("seo").call("keywordMetrics", Map.of("keywords", List.of("api gateway"), "country", "us"));

// Backlink API: summary with every data source that answered
JsonNode backlinks = client.group("seo").call("backlinksSummary", Map.of("domain", "example.com"));
System.out.println(backlinks.get("partial") + " " + backlinks.get("sources").fieldNames().next());

try {
    client.group("verify").call("wait", Map.of("number", "+12025550192"));
} catch (AxioApiException.RateLimitException e) {
    System.out.println("retry in " + e.getRetryAfter() + "s, request " + e.getRequestId());
} catch (AxioApiException.InsufficientCreditsException e) {
    System.out.println("top up credits");
}
```

- `client.group("seo").call("keywordMetrics", params)` or `client.call("seo.keyword-metrics", params)` for every endpoint (camelCase, snake_case or kebab-case). `client.operations()` lists all of them with method, path and credit cost.
- Returns the `data` node. `client.request("GET", "/api/v1/account/limits", null, null, true)` returns the whole envelope. `callBinary` returns file bytes.
- Builder options: `baseUrl`, `timeout`, `maxRetries(2)`, `userAgent`. 429 is retried for all methods; 502/503/504 and network errors only for GET and DELETE.
- Unchecked exceptions extend `AxioApiException` (`getStatus`, `getCode`, `getRequestId`, `getFields`).

Docs: https://axioapi.com/docs · Guides: https://axioapi.com/guides · License: MIT
