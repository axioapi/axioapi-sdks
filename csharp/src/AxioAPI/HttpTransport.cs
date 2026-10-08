using System.Text;

namespace AxioAPI;

/// <summary>Status, headers and body of one HTTP response.</summary>
internal sealed record HttpResult(int Status, HttpResponseMessage Message, byte[] Body)
{
    public bool IsSuccess => Status is >= 200 and < 300;

    public bool IsJson => (Message.Content.Headers.ContentType?.MediaType ?? "").Contains("json");

    public string? Header(string name) =>
        Message.Headers.TryGetValues(name, out var values) ? values.FirstOrDefault() : null;
}

/// <summary>One HTTP round trip with HttpClient; HTTP error statuses are returned, not thrown.</summary>
internal sealed class HttpTransport : IDisposable
{
    private readonly HttpClient _http;

    public HttpTransport(HttpMessageHandler? handler, TimeSpan timeout)
    {
        _http = handler is null ? new HttpClient() : new HttpClient(handler);
        _http.Timeout = timeout;
    }

    /// <exception cref="HttpRequestException">no response arrived (also timeouts as TaskCanceledException)</exception>
    public async Task<HttpResult> SendAsync(string method, string url, IReadOnlyDictionary<string, string> headers, string? payload, CancellationToken token)
    {
        using var request = new HttpRequestMessage(new HttpMethod(method), url);
        foreach (var (name, value) in headers) request.Headers.TryAddWithoutValidation(name, value);
        if (payload is not null) request.Content = new StringContent(payload, Encoding.UTF8, "application/json");

        var response = await _http.SendAsync(request, token).ConfigureAwait(false);
        var body = await response.Content.ReadAsByteArrayAsync(token).ConfigureAwait(false);
        return new HttpResult((int)response.StatusCode, response, body);
    }

    public void Dispose() => _http.Dispose();
}
