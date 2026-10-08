namespace AxioAPI;

/// <summary>402: not enough credits.</summary>
public sealed class AxioApiInsufficientCreditsException : AxioApiException
{
    public AxioApiInsufficientCreditsException(string message, int? status, string? code, string? requestId, IReadOnlyDictionary<string, string[]>? fields)
        : base(message, status, code, requestId, fields)
    {
    }
}
