using System.Globalization;
using System.Text.Json;

namespace AxioAPI;

/// <summary>Turns HTTP responses into data or typed exceptions.</summary>
internal static class ResponseParser
{
    /// <summary>Returns the data element, the whole envelope when raw, or bytes for non-JSON bodies (files).</summary>
    public static (JsonElement? Json, byte[]? Bytes) Parse(HttpResult response, bool raw)
    {
        if (!response.IsJson) return (null, response.Body);
        if (response.Body.Length == 0) return (null, null);
        using var document = JsonDocument.Parse(response.Body);
        var root = document.RootElement;
        if (raw || root.ValueKind != JsonValueKind.Object) return (root.Clone(), null);
        return (root.TryGetProperty("data", out var data) ? data.Clone() : default(JsonElement), null);
    }

    public static AxioApiException Error(HttpResult response)
    {
        var detail = ReadDetail(response);
        var status = response.Status;
        return status switch
        {
            401 => new AxioApiAuthenticationException(detail.Message, status, detail.Code, detail.RequestId, detail.Fields),
            402 => new AxioApiInsufficientCreditsException(detail.Message, status, detail.Code, detail.RequestId, detail.Fields),
            404 => new AxioApiNotFoundException(detail.Message, status, detail.Code, detail.RequestId, detail.Fields),
            422 => new AxioApiValidationException(detail.Message, status, detail.Code, detail.RequestId, detail.Fields),
            429 => new AxioApiRateLimitException(detail.Message, status, detail.Code, detail.RequestId, detail.Fields) { RetryAfter = RetryAfter(response) },
            _ => new AxioApiException(detail.Message, status, detail.Code, detail.RequestId, detail.Fields),
        };
    }

    private sealed record ErrorDetail(string Message, string? Code, string? RequestId, Dictionary<string, string[]> Fields);

    private static ErrorDetail ReadDetail(HttpResult response)
    {
        var message = $"HTTP {response.Status}";
        string? code = null, requestId = response.Header("X-Request-Id");
        var fields = new Dictionary<string, string[]>();
        try
        {
            using var document = JsonDocument.Parse(response.Body);
            var root = document.RootElement;
            if (root.ValueKind == JsonValueKind.Object)
            {
                message = Text(root, "message") ?? message;
                if (root.TryGetProperty("error", out var error) && error.ValueKind == JsonValueKind.Object)
                {
                    message = Text(error, "message") ?? message;
                    code = Text(error, "code");
                    requestId = Text(error, "request_id") ?? requestId;
                    ReadFields(error, fields);
                }
            }
        }
        catch (JsonException)
        {
            // Not JSON: keep the generic message.
        }
        return new ErrorDetail(message, code, requestId, fields);
    }

    private static void ReadFields(JsonElement error, Dictionary<string, string[]> fields)
    {
        if (!error.TryGetProperty("fields", out var node) || node.ValueKind != JsonValueKind.Object) return;
        foreach (var field in node.EnumerateObject())
        {
            fields[field.Name] = field.Value.ValueKind == JsonValueKind.Array
                ? field.Value.EnumerateArray().Select(item => item.ToString()).ToArray()
                : new[] { field.Value.ToString() };
        }
    }

    private static string? Text(JsonElement node, string property) =>
        node.TryGetProperty(property, out var value) && value.ValueKind == JsonValueKind.String ? value.GetString() : null;

    private static double? RetryAfter(HttpResult response) =>
        double.TryParse(response.Header("Retry-After"), NumberStyles.Float, CultureInfo.InvariantCulture, out var seconds) ? seconds : null;
}
