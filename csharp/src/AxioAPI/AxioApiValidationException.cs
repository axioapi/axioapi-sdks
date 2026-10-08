namespace AxioAPI;

/// <summary>422: invalid parameters; see <see cref="AxioApiException.Fields"/>.</summary>
public sealed class AxioApiValidationException : AxioApiException
{
    public AxioApiValidationException(string message, int? status, string? code, string? requestId, IReadOnlyDictionary<string, string[]>? fields)
        : base(message, status, code, requestId, fields)
    {
    }
}
