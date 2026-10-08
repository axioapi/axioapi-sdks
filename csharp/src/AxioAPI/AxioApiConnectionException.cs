namespace AxioAPI;

/// <summary>The request never produced an HTTP response (DNS, TLS, timeout).</summary>
public sealed class AxioApiConnectionException : AxioApiException
{
    public AxioApiConnectionException(string message)
        : base(message)
    {
    }
}
