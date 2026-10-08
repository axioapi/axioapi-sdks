using System.Net;
using System.Reflection;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace AxioAPI;

/// <summary>Options for <see cref="AxioApiClient"/>.</summary>
public sealed class AxioApiOptions
{
    public string? BaseUrl { get; set; }
    public TimeSpan Timeout { get; set; } = TimeSpan.FromSeconds(30);
    /// <summary>Retries for 429 and, on GET/DELETE, 502/503/504 and network errors.</summary>
    public int MaxRetries { get; set; } = 2;
    public string? UserAgent { get; set; }
    public HttpMessageHandler? Handler { get; set; }
    public Func<TimeSpan, CancellationToken, Task>? Delay { get; set; }
}

/// <summary>Client for https://axioapi.com. Create an API key in your account and pass it here or set AXIOAPI_KEY.</summary>
public sealed class AxioApiClient : IDisposable
{
    public const string Version = "1.0.0";
    private const string DefaultBaseUrl = "https://axioapi.com";

    private readonly HttpClient _http;
    private readonly string _apiKey;
    private readonly string _baseUrl;
    private readonly string _userAgent;
    private readonly int _maxRetries;
    private readonly Func<TimeSpan, CancellationToken, Task> _delay;
    private readonly Dictionary<string, Operation> _operations = new();
    private readonly Dictionary<string, string> _index = new();

    public AxioApiClient(string? apiKey = null, AxioApiOptions? options = null)
    {
        options ??= new AxioApiOptions();
        _apiKey = apiKey ?? Environment.GetEnvironmentVariable("AXIOAPI_KEY") ?? "";
        if (_apiKey.Length == 0) throw new ArgumentException("Pass an API key or set the AXIOAPI_KEY environment variable.");
        _baseUrl = (options.BaseUrl ?? Environment.GetEnvironmentVariable("AXIOAPI_BASE_URL") ?? DefaultBaseUrl).TrimEnd('/');
        _userAgent = options.UserAgent ?? $"axioapi-dotnet/{Version}";
        _maxRetries = Math.Max(0, options.MaxRetries);
        _delay = options.Delay ?? ((span, token) => Task.Delay(span, token));
        _http = options.Handler is null ? new HttpClient() : new HttpClient(options.Handler);
        _http.Timeout = options.Timeout;
        LoadOperations();
    }

    /// <summary>Every operation: method, path, parameters, credit cost.</summary>
    public IReadOnlyDictionary<string, Operation> Operations => _operations;

    /// <summary>Operation group, for example <c>client.Group("seo").CallAsync("keywordMetrics", ...)</c>.</summary>
    public OperationGroup Group(string name) => new(this, Normalize(name));

    /// <summary>Capability key for a group and operation name, or null.</summary>
    public string? Resolve(string group, string name) => _index.GetValueOrDefault($"{Normalize(group)}.{Normalize(name)}");

    /// <summary>Call an operation by capability key (for example "seo.keyword-metrics"). Returns the <c>data</c> element.</summary>
    public async Task<JsonElement> CallAsync(string operation, object? parameters = null, CancellationToken cancellationToken = default)
    {
        var (op, method, path, query, body) = Prepare(operation, parameters);
        if (op.Binary) throw new InvalidOperationException($"{operation} returns a file; use CallBinaryAsync.");
        var result = await SendAsync(method, path, query, body, false, cancellationToken).ConfigureAwait(false);
        return result.Json ?? default;
    }

    /// <summary>Call an operation that returns a file (image or audio).</summary>
    public async Task<byte[]> CallBinaryAsync(string operation, object? parameters = null, CancellationToken cancellationToken = default)
    {
        var (_, method, path, query, body) = Prepare(operation, parameters);
        var result = await SendAsync(method, path, query, body, false, cancellationToken).ConfigureAwait(false);
        return result.Bytes ?? throw new InvalidOperationException($"{operation} did not return a file.");
    }

    /// <summary>Send a request. Returns <c>data</c>, or the whole envelope when <paramref name="raw"/> is true.</summary>
    public async Task<JsonElement> RequestAsync(string method, string path, object? query = null, object? body = null, bool raw = false, CancellationToken cancellationToken = default)
    {
        var result = await SendAsync(method, path, ToDictionary(query), body, raw, cancellationToken).ConfigureAwait(false);
        return result.Json ?? default;
    }

    private (Operation Op, string Method, string Path, Dictionary<string, object?> Query, Dictionary<string, object?>? Body) Prepare(string operation, object? parameters)
    {
        string? key = _operations.ContainsKey(operation) ? operation : null;
        if (key is null)
        {
            var dot = operation.IndexOf('.');
            if (dot > 0) key = Resolve(operation[..dot], operation[(dot + 1)..]);
        }
        if (key is null) throw new ArgumentException($"Unknown operation '{operation}'. See Operations.");
        var op = _operations[key];
        var rest = ToDictionary(parameters);
        var path = op.Path;
        foreach (var name in op.PathParams)
        {
            if (!rest.TryGetValue(name, out var value) || value is null) throw new ArgumentException($"Missing path parameter '{name}' for {key}");
            path = path.Replace("{" + name + "}", Uri.EscapeDataString(Convert.ToString(value, System.Globalization.CultureInfo.InvariantCulture) ?? ""));
            rest.Remove(name);
        }
        var hasBody = op.Method is not ("GET" or "DELETE" or "HEAD");
        var query = new Dictionary<string, object?>();
        var body = new Dictionary<string, object?>();
        foreach (var (name, value) in rest)
        {
            if (value is null) continue;
            if (hasBody && (op.Body.Contains(name) || !op.Query.Contains(name))) body[name] = value;
            else query[name] = value;
        }
        return (op, op.Method, path, query, hasBody && body.Count > 0 ? body : null);
    }

    private sealed record Result(JsonElement? Json, byte[]? Bytes);

    private async Task<Result> SendAsync(string method, string path, Dictionary<string, object?> query, object? body, bool raw, CancellationToken token)
    {
        method = method.ToUpperInvariant();
        var url = _baseUrl + (path.StartsWith('/') ? path : "/" + path);
        if (query.Count > 0) url += "?" + BuildQuery(query);
        var payload = body is null ? null : JsonSerializer.Serialize(body);
        var idempotent = method is "GET" or "HEAD" or "DELETE";

        for (var attempt = 0; ; attempt++)
        {
            using var request = new HttpRequestMessage(new HttpMethod(method), url);
            request.Headers.TryAddWithoutValidation("Authorization", "Bearer " + _apiKey);
            request.Headers.TryAddWithoutValidation("Accept", "application/json");
            request.Headers.TryAddWithoutValidation("User-Agent", _userAgent);
            if (payload is not null) request.Content = new StringContent(payload, Encoding.UTF8, "application/json");

            HttpResponseMessage response;
            try
            {
                response = await _http.SendAsync(request, token).ConfigureAwait(false);
            }
            catch (Exception ex) when (ex is HttpRequestException or TaskCanceledException && !token.IsCancellationRequested)
            {
                if (idempotent && attempt < _maxRetries)
                {
                    await _delay(Backoff(null, attempt), token).ConfigureAwait(false);
                    continue;
                }
                throw new AxioApiConnectionException($"Could not reach {_baseUrl}: {ex.Message}");
            }

            using (response)
            {
                var bytes = await response.Content.ReadAsByteArrayAsync(token).ConfigureAwait(false);
                var status = (int)response.StatusCode;
                if (status is >= 200 and < 300) return Parse(response, bytes, raw);
                var retryable = status == 429 || (idempotent && status is 502 or 503 or 504);
                if (retryable && attempt < _maxRetries)
                {
                    await _delay(Backoff(HeaderValue(response, "Retry-After"), attempt), token).ConfigureAwait(false);
                    continue;
                }
                throw BuildError(status, response, bytes);
            }
        }
    }

    private static Result Parse(HttpResponseMessage response, byte[] bytes, bool raw)
    {
        var type = response.Content.Headers.ContentType?.MediaType ?? "";
        if (!type.Contains("json")) return new Result(null, bytes);
        if (bytes.Length == 0) return new Result(null, null);
        using var doc = JsonDocument.Parse(bytes);
        var root = doc.RootElement;
        if (raw || root.ValueKind != JsonValueKind.Object) return new Result(root.Clone(), null);
        return new Result(root.TryGetProperty("data", out var data) ? data.Clone() : default, null);
    }

    private static AxioApiException BuildError(int status, HttpResponseMessage response, byte[] bytes)
    {
        string message = $"HTTP {status}";
        string? code = null, requestId = HeaderValue(response, "X-Request-Id");
        var fields = new Dictionary<string, string[]>();
        try
        {
            using var doc = JsonDocument.Parse(bytes);
            var root = doc.RootElement;
            if (root.ValueKind == JsonValueKind.Object)
            {
                if (root.TryGetProperty("message", out var m) && m.ValueKind == JsonValueKind.String) message = m.GetString()!;
                if (root.TryGetProperty("error", out var err) && err.ValueKind == JsonValueKind.Object)
                {
                    if (err.TryGetProperty("message", out var em) && em.ValueKind == JsonValueKind.String) message = em.GetString()!;
                    if (err.TryGetProperty("code", out var c) && c.ValueKind == JsonValueKind.String) code = c.GetString();
                    if (err.TryGetProperty("request_id", out var r) && r.ValueKind == JsonValueKind.String) requestId = r.GetString();
                    if (err.TryGetProperty("fields", out var f) && f.ValueKind == JsonValueKind.Object)
                        foreach (var p in f.EnumerateObject())
                            fields[p.Name] = p.Value.ValueKind == JsonValueKind.Array ? p.Value.EnumerateArray().Select(x => x.ToString()).ToArray() : new[] { p.Value.ToString() };
                }
            }
        }
        catch (JsonException) { /* not JSON */ }

        return status switch
        {
            401 => new AxioApiAuthenticationException(message, status, code, requestId, fields),
            402 => new AxioApiInsufficientCreditsException(message, status, code, requestId, fields),
            404 => new AxioApiNotFoundException(message, status, code, requestId, fields),
            422 => new AxioApiValidationException(message, status, code, requestId, fields),
            429 => new AxioApiRateLimitException(message, status, code, requestId, fields)
            {
                RetryAfter = double.TryParse(HeaderValue(response, "Retry-After"), System.Globalization.NumberStyles.Float, System.Globalization.CultureInfo.InvariantCulture, out var seconds) ? seconds : null,
            },
            _ => new AxioApiException(message, status, code, requestId, fields),
        };
    }

    private static string? HeaderValue(HttpResponseMessage response, string name) =>
        response.Headers.TryGetValues(name, out var values) ? values.FirstOrDefault() : null;

    private static TimeSpan Backoff(string? retryAfter, int attempt)
    {
        if (retryAfter is not null && double.TryParse(retryAfter, System.Globalization.NumberStyles.Float, System.Globalization.CultureInfo.InvariantCulture, out var seconds))
            return TimeSpan.FromSeconds(Math.Min(seconds, 30));
        return TimeSpan.FromSeconds(Math.Min(0.5 * Math.Pow(2, attempt), 8));
    }

    private static string BuildQuery(Dictionary<string, object?> query)
    {
        var pairs = new List<string>();
        foreach (var (key, value) in query)
        {
            if (value is null) continue;
            if (value is System.Collections.IEnumerable list and not string)
                foreach (var item in list) pairs.Add($"{Uri.EscapeDataString(key + "[]")}={Uri.EscapeDataString(Format(item))}");
            else pairs.Add($"{Uri.EscapeDataString(key)}={Uri.EscapeDataString(Format(value))}");
        }
        return string.Join("&", pairs);
    }

    private static string Format(object? value) => value switch
    {
        bool b => b ? "true" : "false",
        IFormattable f => f.ToString(null, System.Globalization.CultureInfo.InvariantCulture),
        _ => Convert.ToString(value, System.Globalization.CultureInfo.InvariantCulture) ?? "",
    };

    private static Dictionary<string, object?> ToDictionary(object? parameters)
    {
        if (parameters is null) return new();
        if (parameters is IDictionary<string, object?> dict) return new Dictionary<string, object?>(dict);
        if (parameters is System.Collections.IDictionary legacy)
        {
            var result = new Dictionary<string, object?>();
            foreach (System.Collections.DictionaryEntry entry in legacy) result[(string)entry.Key] = entry.Value;
            return result;
        }
        return parameters.GetType().GetProperties(BindingFlags.Public | BindingFlags.Instance)
            .ToDictionary(p => p.Name, p => p.GetValue(parameters), StringComparer.Ordinal);
    }

    internal static string Normalize(string name) => Regex.Replace(name.ToLowerInvariant(), "[^a-z0-9]", "");

    private void LoadOperations()
    {
        using var stream = typeof(AxioApiClient).Assembly.GetManifestResourceStream("AxioAPI.operations.json")
            ?? throw new InvalidOperationException("operations.json resource is missing.");
        using var doc = JsonDocument.Parse(stream);
        foreach (var entry in doc.RootElement.GetProperty("operations").EnumerateObject())
        {
            var o = entry.Value;
            string[] List(string prop) => o.GetProperty(prop).EnumerateArray().Select(x => x.GetString()!).ToArray();
            _operations[entry.Name] = new Operation(
                o.GetProperty("method").GetString()!, o.GetProperty("path").GetString()!, o.GetProperty("summary").GetString() ?? "",
                o.GetProperty("cost").ValueKind == JsonValueKind.Number ? o.GetProperty("cost").GetDouble() : null,
                List("path_params"), List("query"), List("body"), o.GetProperty("binary").GetBoolean());
            var dot = entry.Name.IndexOf('.');
            _index[$"{Normalize(entry.Name[..dot])}.{Normalize(entry.Name[(dot + 1)..])}"] = entry.Name;
        }
    }

    public void Dispose() => _http.Dispose();
}

/// <summary>One API operation as described in the registry.</summary>
public sealed record Operation(string Method, string Path, string Summary, double? Cost, string[] PathParams, string[] Query, string[] Body, bool Binary);

/// <summary><c>client.Group("seo")</c>: call an operation of one API group by name.</summary>
public sealed class OperationGroup
{
    private readonly AxioApiClient _client;
    private readonly string _group;

    internal OperationGroup(AxioApiClient client, string group)
    {
        _client = client;
        _group = group;
    }

    public Task<JsonElement> CallAsync(string name, object? parameters = null, CancellationToken cancellationToken = default)
    {
        var key = _client.Resolve(_group, name) ?? throw new ArgumentException($"AxioAPI has no operation '{_group}.{name}'.");
        return _client.CallAsync(key, parameters, cancellationToken);
    }

    public Task<byte[]> CallBinaryAsync(string name, object? parameters = null, CancellationToken cancellationToken = default)
    {
        var key = _client.Resolve(_group, name) ?? throw new ArgumentException($"AxioAPI has no operation '{_group}.{name}'.");
        return _client.CallBinaryAsync(key, parameters, cancellationToken);
    }
}
