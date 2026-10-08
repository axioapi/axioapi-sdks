namespace AxioAPI;

/// <summary>404: the resource does not exist, expired or is not yours.</summary>
public sealed class AxioApiNotFoundException : AxioApiException
{
    public AxioApiNotFoundException(string message, int? status, string? code, string? requestId, IReadOnlyDictionary<string, string[]>? fields)
        : base(message, status, code, requestId, fields)
    {
    }
}
