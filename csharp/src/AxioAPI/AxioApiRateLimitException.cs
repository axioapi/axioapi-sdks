namespace AxioAPI;

/// <summary>429: request limit reached; <see cref="RetryAfter"/> is in seconds when the server sent it.</summary>
public sealed class AxioApiRateLimitException : AxioApiException
{
    public AxioApiRateLimitException(string message, int? status, string? code, string? requestId, IReadOnlyDictionary<string, string[]>? fields)
        : base(message, status, code, requestId, fields)
    {
    }

    public double? RetryAfter { get; init; }
}
