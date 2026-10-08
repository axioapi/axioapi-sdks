namespace AxioAPI;

/// <summary>401: the API key is missing or invalid.</summary>
public sealed class AxioApiAuthenticationException : AxioApiException
{
    public AxioApiAuthenticationException(string message, int? status, string? code, string? requestId, IReadOnlyDictionary<string, string[]>? fields)
        : base(message, status, code, requestId, fields)
    {
    }
}
