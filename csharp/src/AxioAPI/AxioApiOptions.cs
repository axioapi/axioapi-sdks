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
