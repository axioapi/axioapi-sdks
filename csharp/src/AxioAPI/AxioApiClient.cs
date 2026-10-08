using System.Text.Json;

namespace AxioAPI;

/// <summary>Client for https://axioapi.com. Pass an API key or set AXIOAPI_KEY.</summary>
public sealed class AxioApiClient : IDisposable
{
    public const string Version = "1.0.0";
    private const string DefaultBaseUrl = "https://axioapi.com";

    private readonly string _apiKey;
    private readonly string _baseUrl;
    private readonly string _userAgent;
    private readonly Registry _registry = Registry.Load();
    private readonly RetryPolicy _retry;
    private readonly HttpTransport _transport;

    public AxioApiClient(string? apiKey = null, AxioApiOptions? options = null)
    {
        options ??= new AxioApiOptions();
        _apiKey = FirstNonEmpty(apiKey, Environment.GetEnvironmentVariable("AXIOAPI_KEY"))
            ?? throw new ArgumentException("Pass an API key or set the AXIOAPI_KEY environment variable.");
        var baseUrl = FirstNonEmpty(options.BaseUrl, Environment.GetEnvironmentVariable("AXIOAPI_BASE_URL")) ?? DefaultBaseUrl;
        _baseUrl = baseUrl.TrimEnd('/');
        _userAgent = options.UserAgent ?? $"axioapi-dotnet/{Version}";
        _retry = new RetryPolicy(options.MaxRetries, options.Delay ?? ((span, token) => Task.Delay(span, token)));
        _transport = new HttpTransport(options.Handler, options.Timeout);
    }

    /// <summary>Every operation: method, path, parameters, credit cost.</summary>
    public IReadOnlyDictionary<string, Operation> Operations => _registry.All;

    /// <summary>Operation group, for example <c>client.Group("seo").CallAsync("keywordMetrics", ...)</c>.</summary>
    public OperationGroup Group(string name) => new(this, name);

    /// <summary>Capability key for a group and operation name (any spelling), or null.</summary>
    public string? Resolve(string group, string name) => _registry.Resolve(group, name);

    /// <summary>Calls an operation by capability key (for example "seo.keyword-metrics") and returns the data element.</summary>
    public async Task<JsonElement> CallAsync(string operation, object? parameters = null, CancellationToken cancellationToken = default)
    {
        var found = Require(operation);
        if (found.Binary) throw new InvalidOperationException($"{operation} returns a file; use CallBinaryAsync.");
        var (json, _) = await ExecuteAsync(found, parameters, cancellationToken).ConfigureAwait(false);
        return json ?? default;
    }

    /// <summary>Calls an operation that returns a file (image or audio).</summary>
    public async Task<byte[]> CallBinaryAsync(string operation, object? parameters = null, CancellationToken cancellationToken = default)
    {
        var (_, bytes) = await ExecuteAsync(Require(operation), parameters, cancellationToken).ConfigureAwait(false);
        return bytes ?? throw new InvalidOperationException($"{operation} did not return a file.");
    }

    /// <summary>Sends a request with retries; returns <c>data</c>, or the whole envelope when <paramref name="raw"/> is true.</summary>
    public async Task<JsonElement> RequestAsync(string method, string path, object? query = null, object? body = null, bool raw = false, CancellationToken cancellationToken = default)
    {
        var (json, _) = await SendAsync(method, path, RequestBuilder.ToDictionary(query), body, raw, cancellationToken).ConfigureAwait(false);
        return json ?? default;
    }

    private Operation Require(string operation) =>
        _registry.Find(operation) ?? throw new ArgumentException($"Unknown operation '{operation}'. See Operations.");

    private Task<(JsonElement? Json, byte[]? Bytes)> ExecuteAsync(Operation operation, object? parameters, CancellationToken token)
    {
        var prepared = RequestBuilder.Build(operation, parameters);
        return SendAsync(prepared.Method, prepared.Path, prepared.Query, prepared.Body, false, token);
    }

    private async Task<(JsonElement? Json, byte[]? Bytes)> SendAsync(string method, string path, Dictionary<string, object?> query, object? body, bool raw, CancellationToken token)
    {
        var verb = method.ToUpperInvariant();
        var url = BuildUrl(path, query);
        var payload = body is null ? null : JsonSerializer.Serialize(body);
        var headers = BuildHeaders();

        for (var attempt = 0; ; attempt++)
        {
            HttpResult response;
            try
            {
                response = await _transport.SendAsync(verb, url, headers, payload, token).ConfigureAwait(false);
            }
            catch (Exception ex) when (ex is HttpRequestException or TaskCanceledException && !token.IsCancellationRequested)
            {
                if (!_retry.ShouldRetryConnection(verb, attempt))
                {
                    throw new AxioApiConnectionException($"Could not reach {_baseUrl}: {ex.Message}");
                }
                await _retry.WaitAsync(attempt, null, token).ConfigureAwait(false);
                continue;
            }

            using (response.Message)
            {
                if (response.IsSuccess) return ResponseParser.Parse(response, raw);
                if (!_retry.ShouldRetryStatus(response.Status, verb, attempt)) throw ResponseParser.Error(response);
                await _retry.WaitAsync(attempt, response.Header("Retry-After"), token).ConfigureAwait(false);
            }
        }
    }

    private string BuildUrl(string path, Dictionary<string, object?> query)
    {
        var url = _baseUrl + (path.StartsWith('/') ? path : "/" + path);
        return query.Count == 0 ? url : $"{url}?{RequestBuilder.EncodeQuery(query)}";
    }

    private Dictionary<string, string> BuildHeaders() => new()
    {
        ["Authorization"] = "Bearer " + _apiKey,
        ["Accept"] = "application/json",
        ["User-Agent"] = _userAgent,
    };

    private static string? FirstNonEmpty(params string?[] values) => values.FirstOrDefault(value => !string.IsNullOrEmpty(value));

    public void Dispose() => _transport.Dispose();
}
