namespace AxioAPI;

/// <summary>Base class for API errors; carries the HTTP status, error code and request id for support.</summary>
public class AxioApiException : Exception
{
    public AxioApiException(string message, int? status = null, string? code = null, string? requestId = null, IReadOnlyDictionary<string, string[]>? fields = null)
        : base(message)
    {
        Status = status;
        Code = code;
        RequestId = requestId;
        Fields = fields ?? new Dictionary<string, string[]>();
    }

    public int? Status { get; }
    public string? Code { get; }
    public string? RequestId { get; }
    public IReadOnlyDictionary<string, string[]> Fields { get; }
}

/// <summary>401: the API key is missing or invalid.</summary>
public sealed class AxioApiAuthenticationException : AxioApiException
{
    public AxioApiAuthenticationException(string message, int? status, string? code, string? requestId, IReadOnlyDictionary<string, string[]>? fields) : base(message, status, code, requestId, fields) { }
}

/// <summary>402: not enough credits.</summary>
public sealed class AxioApiInsufficientCreditsException : AxioApiException
{
    public AxioApiInsufficientCreditsException(string message, int? status, string? code, string? requestId, IReadOnlyDictionary<string, string[]>? fields) : base(message, status, code, requestId, fields) { }
}

/// <summary>404: the resource does not exist, expired or is not yours.</summary>
public sealed class AxioApiNotFoundException : AxioApiException
{
    public AxioApiNotFoundException(string message, int? status, string? code, string? requestId, IReadOnlyDictionary<string, string[]>? fields) : base(message, status, code, requestId, fields) { }
}

/// <summary>422: invalid parameters; see <see cref="AxioApiException.Fields"/>.</summary>
public sealed class AxioApiValidationException : AxioApiException
{
    public AxioApiValidationException(string message, int? status, string? code, string? requestId, IReadOnlyDictionary<string, string[]>? fields) : base(message, status, code, requestId, fields) { }
}

/// <summary>429: request limit reached; <see cref="RetryAfter"/> is in seconds when the server sent it.</summary>
public sealed class AxioApiRateLimitException : AxioApiException
{
    public AxioApiRateLimitException(string message, int? status, string? code, string? requestId, IReadOnlyDictionary<string, string[]>? fields) : base(message, status, code, requestId, fields) { }

    public double? RetryAfter { get; init; }
}

/// <summary>The request never produced an HTTP response (DNS, TLS, timeout).</summary>
public sealed class AxioApiConnectionException : AxioApiException
{
    public AxioApiConnectionException(string message) : base(message) { }
}
