# AxioAPI for .NET

C# client for the [AxioAPI](https://axioapi.com) REST API: temp mail API, receive SMS and OTP API, email validation API, proxy API, SEO API (keyword data and backlink API) and social scraper APIs. One API key, pay per request. .NET 6+, no dependencies beyond `System.Text.Json`.

```bash
dotnet add package AxioAPI
export AXIOAPI_KEY=ak_...
```

```csharp
using AxioAPI;

using var client = new AxioApiClient();   // or new AxioApiClient("ak_...")

// Keyword data API: volume, CPC and competition for up to 10 keywords per request
var rows = await client.Group("seo").CallAsync("keywordMetrics", new { keywords = new[] { "api gateway" }, country = "us" });

// Backlink API: summary with every data source that answered
var backlinks = await client.CallAsync("seo.backlinks-summary", new { domain = "example.com" });
Console.WriteLine(backlinks.GetProperty("partial"));

try
{
    await client.Group("verify").CallAsync("wait", new { number = "+12025550192" });
}
catch (AxioApiRateLimitException e)
{
    Console.WriteLine($"retry in {e.RetryAfter}s, request {e.RequestId}");
}
catch (AxioApiInsufficientCreditsException)
{
    Console.WriteLine("top up credits");
}
```

- `client.Group("seo").CallAsync("keywordMetrics", ...)` or `client.CallAsync("seo.keyword-metrics", ...)` for every endpoint (camelCase, snake_case or kebab-case). `client.Operations` lists all of them with method, path and credit cost. Parameters can be an anonymous object or a dictionary.
- Returns the `data` element as `JsonElement`. `client.RequestAsync("GET", "/api/v1/account/limits", raw: true)` returns the whole envelope. `CallBinaryAsync` returns file bytes.
- Options: `new AxioApiOptions { BaseUrl, Timeout, MaxRetries = 2 }`. 429 is retried for all methods; 502/503/504 and network errors only for GET and DELETE.
- Errors derive from `AxioApiException` (`Status`, `Code`, `RequestId`, `Fields`).

Docs: https://axioapi.com/docs · Guides: https://axioapi.com/guides · License: MIT
