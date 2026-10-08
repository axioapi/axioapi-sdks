# AxioAPI Java SDK: temp mail, SMS OTP, email validation, proxy, SEO and backlink API

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
} catch (RateLimitException e) {
    System.out.println("retry in " + e.getRetryAfter() + "s, request " + e.getRequestId());
} catch (InsufficientCreditsException e) {
    System.out.println("top up credits");
}
```

- `client.group("seo").call("keywordMetrics", params)` or `client.call("seo.keyword-metrics", params)` for every endpoint (camelCase, snake_case or kebab-case). `client.operations()` lists all of them with method, path and credit cost.
- Returns the `data` node. `client.request("GET", "/api/v1/account/limits", null, null, true)` returns the whole envelope. `callBinary` returns file bytes.
- Builder options: `baseUrl`, `timeout`, `maxRetries(2)`, `userAgent`. 429 is retried for all methods; 502/503/504 and network errors only for GET and DELETE.
- Unchecked exceptions extend `AxioApiException` (`getStatus`, `getCode`, `getRequestId`, `getFields`).

Docs: https://axioapi.com/docs · Guides: https://axioapi.com/guides · License: MIT

<!-- seo:start -->
## What you can build with the Java SDK

| Use case | API page | Typical call |
|---|---|---|
| [Temp mail API](https://axioapi.com/apis/temporary-email): disposable inboxes for signup and password-reset tests | `temporary-email` | create inbox, list messages, read message |
| [Receive SMS API](https://axioapi.com/apis/sms) and [OTP API](https://axioapi.com/apis/verify): public numbers and "wait for the code" | `sms`, `verify` | list numbers, wait for OTP |
| [Email validation API](https://axioapi.com/apis/email-validation): syntax, MX and disposable-address checks | `email` | validate, batch validate |
| [Proxy API](https://axioapi.com/apis/proxy-vpn): sticky and rotating proxy sessions by country | `proxy` | create session, rotate, close |
| [SEO API](https://axioapi.com/apis/seo): keyword data API (volume, CPC), backlink API, domain overview and history, on-page audit | `seo` | keyword metrics, backlinks summary |
| [TikTok](https://axioapi.com/apis/social-tiktok), [Facebook](https://axioapi.com/apis/social-facebook), [Instagram](https://axioapi.com/apis/social-instagram), [YouTube](https://axioapi.com/apis/social-youtube), [X (Twitter)](https://axioapi.com/apis/social-twitter), [LinkedIn](https://axioapi.com/apis/social-linkedin) and [Reddit](https://axioapi.com/apis/social-reddit) scraper APIs | `social` | profiles, posts, comments, transcripts |

## Guides with working code

- [Backlink API: check a domain's backlinks in Python](https://axioapi.com/guides/backlink-api-check-domain-python)
- [Keyword data API: get search volume and CPC in code](https://axioapi.com/guides/keyword-data-api-volume-cpc)
- [SEO report API: build a domain ranking report](https://axioapi.com/guides/seo-report-api-domain-ranking)
- [Test signup emails with Playwright and a temp mail API](https://axioapi.com/guides/playwright-temp-mail-signup-test)
- [Test OTP flows with a receive SMS API](https://axioapi.com/guides/otp-testing-with-sms-api)
- [Sticky vs rotating proxy: which one to use](https://axioapi.com/guides/sticky-vs-rotating-proxy)

Free tools that need no account: [backlink checker](https://axioapi.com/tools/backlink-checker), [email validator](https://axioapi.com/tools/email-validator), [DNS lookup](https://axioapi.com/tools/dns-lookup), [BIN checker](https://axioapi.com/tools/bin-checker).

## FAQ

**Is there a Java client for the AxioAPI backlink API and keyword data API?** Yes, this package. `seo.backlinks_summary` returns the backlink summary of a domain with the figures of every data source, and `seo.keyword_metrics` returns search volume, CPC and competition for up to 10 keywords per request.

**How do I test signup emails and OTP codes from Java?** Create a disposable inbox with the temp mail API, submit its address in your form and read the message. For SMS codes, call the verify endpoint, which waits for the OTP on a public number and returns it.

**How much does it cost?** You pay per request with credits, and each endpoint lists its price on its [API page](https://axioapi.com/apis) and in the OpenAPI spec (`x-credit-cost`). New accounts receive free credits after verification. See [pricing](https://axioapi.com/pricing).

**Where is the full reference?** [axioapi.com/docs](https://axioapi.com/docs), the [OpenAPI 3.1 spec](https://axioapi.com/api/v1/openapi.json) and [llms.txt](https://axioapi.com/llms.txt) for AI agents.

Vietnamese: [AxioAPI tiếng Việt](https://axioapi.com/vi), [API SEO và API backlink](https://axioapi.com/vi/apis/seo).
<!-- seo:end -->
