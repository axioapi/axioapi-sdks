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

    /// <summary>Per-field messages for 422 responses.</summary>
    public IReadOnlyDictionary<string, string[]> Fields { get; }
}
