using System.Net.Http;
using System.Text.Json;
using AxioAPI;
using Xunit;

namespace AxioAPI.Tests;

public class ClientTests : IDisposable
{
    private static readonly string Url = Environment.GetEnvironmentVariable("AXIOAPI_MOCK_URL") ?? "http://127.0.0.1:8765";
    private static readonly HttpClient Plain = new();

    public ClientTests() => Plain.GetStringAsync(Url + "/__reset").GetAwaiter().GetResult();

    public void Dispose() { }

    private static AxioApiClient Make(string key = "test_key", int maxRetries = 2, TimeSpan? timeout = null, string? url = null) =>
        new(key, new AxioApiOptions { BaseUrl = url ?? Url, MaxRetries = maxRetries, Delay = (_, _) => Task.CompletedTask, Timeout = timeout ?? TimeSpan.FromSeconds(30) });

    private static JsonDocument Hits() => JsonDocument.Parse(Plain.GetStringAsync(Url + "/__hits").GetAwaiter().GetResult());

    [Fact]
    public void RequiresKey()
    {
        Environment.SetEnvironmentVariable("AXIOAPI_KEY", null);
        Assert.Throws<ArgumentException>(() => new AxioApiClient(null, new AxioApiOptions { BaseUrl = Url }));
    }

    [Fact]
    public async Task GroupCallSendsJsonBodyAndUnwrapsData()
    {
        var data = await Make().Group("seo").CallAsync("keywordMetrics", new { keywords = new[] { "api gateway", "proxy scraper" }, country = "us" });
        Assert.Equal("POST", data.GetProperty("method").GetString());
        Assert.Equal("/api/v1/seo/keywords/metrics", data.GetProperty("path").GetString());
        Assert.Equal("us", data.GetProperty("body").GetProperty("country").GetString());
        Assert.Equal(2, data.GetProperty("body").GetProperty("keywords").GetArrayLength());
        Assert.StartsWith("application/json", data.GetProperty("content_type").GetString());
        Assert.StartsWith("axioapi-dotnet/", data.GetProperty("user_agent").GetString());
    }

    [Fact]
    public async Task PathParamsEncodedAndQuerySeparated()
    {
        var c = Make();
        var data = await c.CallAsync("seo.backlinks-summary", new { domain = "exa mple.com" });
        Assert.Equal("/api/v1/seo/domains/exa%20mple.com/backlinks", data.GetProperty("path").GetString());
        var q = await c.Group("seo").CallAsync("keyword_suggestions", new Dictionary<string, object?> { ["q"] = "laravel api", ["country"] = "us", ["lang"] = "en", ["skipped"] = null });
        Assert.Equal("laravel api", q.GetProperty("query").GetProperty("q")[0].GetString());
        Assert.False(q.GetProperty("query").TryGetProperty("skipped", out _));
    }

    [Fact]
    public async Task MissingPathParamAndUnknownOperation()
    {
        var c = Make();
        await Assert.ThrowsAsync<ArgumentException>(() => c.CallAsync("seo.backlinks-summary"));
        await Assert.ThrowsAsync<ArgumentException>(() => c.CallAsync("nope.nothing"));
        await Assert.ThrowsAsync<ArgumentException>(() => c.Group("seo").CallAsync("doesNotExist"));
    }

    [Fact]
    public void EveryRegistryOperationIsReachable()
    {
        var c = Make();
        Assert.True(c.Operations.Count > 100);
        foreach (var key in c.Operations.Keys)
        {
            var dot = key.IndexOf('.');
            Assert.Equal(key, c.Resolve(key[..dot], key[(dot + 1)..]));
        }
    }

    [Fact]
    public async Task RawEnvelope()
    {
        var env = await Make().RequestAsync("GET", "/api/v1/account/limits", raw: true);
        Assert.Equal("success", env.GetProperty("status").GetString());
        Assert.Equal("req_test_1", env.GetProperty("request").GetProperty("id").GetString());
    }

    [Fact]
    public async Task BinaryDownloadReturnsBytes()
    {
        var bytes = await Make().CallBinaryAsync("ai-image.artifact", new { job = "abc" });
        Assert.Equal(0x89, bytes[0]);
        await Assert.ThrowsAsync<InvalidOperationException>(() => Make().CallAsync("ai-image.artifact", new { job = "abc" }));
    }

    [Fact]
    public async Task ErrorsMapToClasses()
    {
        var auth = await Assert.ThrowsAsync<AxioApiAuthenticationException>(() => Make("wrong").Group("account").CallAsync("limits"));
        Assert.Equal(401, auth.Status);
        Assert.Equal("req_test_1", auth.RequestId);
        await Assert.ThrowsAsync<AxioApiInsufficientCreditsException>(() => Make("nocredit").Group("account").CallAsync("limits"));
        await Assert.ThrowsAsync<AxioApiNotFoundException>(() => Make().RequestAsync("GET", "/api/v1/temp-mail/inboxes/missing"));
        var validation = await Assert.ThrowsAsync<AxioApiValidationException>(() => Make().Group("seo").CallAsync("onPageAudit"));
        Assert.Equal("The url field is required.", validation.Fields["url"][0]);
        var other = await Assert.ThrowsAsync<AxioApiException>(() => Make().RequestAsync("GET", "/api/v1/boom"));
        Assert.Equal(500, other.Status);
    }

    [Fact]
    public async Task RetriesIdempotent503ThenSucceeds()
    {
        var data = await Make().RequestAsync("GET", "/api/v1/flaky");
        Assert.Equal(3, data.GetProperty("attempts").GetInt32());
    }

    [Fact]
    public async Task Retries429GivesUpWithRetryAfterAndPostNotRetriedOn503()
    {
        var data = await Make().RequestAsync("GET", "/api/v1/ratelimited");
        Assert.Equal(2, data.GetProperty("attempts").GetInt32());
        var rate = await Assert.ThrowsAsync<AxioApiRateLimitException>(() => Make(maxRetries: 1).RequestAsync("POST", "/api/v1/always429", body: new { }));
        Assert.Equal(7.0, rate.RetryAfter);
        Assert.Equal(2, Hits().RootElement.GetProperty("always429").GetInt32());
        await Assert.ThrowsAsync<AxioApiException>(() => Make().RequestAsync("POST", "/api/v1/post503", body: new { }));
        Assert.Equal(1, Hits().RootElement.GetProperty("post503").GetInt32());
    }

    [Fact]
    public async Task ConnectionError()
    {
        await Assert.ThrowsAsync<AxioApiConnectionException>(() => Make("k", 0, TimeSpan.FromSeconds(2), "http://127.0.0.1:1").Group("account").CallAsync("limits"));
    }
}
