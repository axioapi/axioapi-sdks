using System.Collections;
using System.Globalization;
using System.Reflection;

namespace AxioAPI;

/// <summary>An operation with its parameters placed in path, query and body.</summary>
internal sealed record PreparedRequest(string Method, string Path, Dictionary<string, object?> Query, Dictionary<string, object?>? Body);

/// <summary>Splits flat parameters into path, query and body according to the operation.</summary>
internal static class RequestBuilder
{
    public static PreparedRequest Build(Operation operation, object? parameters)
    {
        var remaining = ToDictionary(parameters);
        var path = FillPath(operation, remaining);
        var query = new Dictionary<string, object?>();
        var body = new Dictionary<string, object?>();
        foreach (var (name, value) in remaining)
        {
            if (value is null) continue;
            (BelongsInBody(operation, name) ? body : query)[name] = value;
        }
        return new PreparedRequest(operation.Method, path, query, operation.HasBody && body.Count > 0 ? body : null);
    }

    public static string EncodeQuery(Dictionary<string, object?> query)
    {
        var pairs = new List<string>();
        foreach (var (key, value) in query)
        {
            if (value is null) continue;
            if (value is IEnumerable list and not string)
            {
                pairs.AddRange(list.Cast<object?>().Select(item => Pair(key + "[]", item)));
            }
            else
            {
                pairs.Add(Pair(key, value));
            }
        }
        return string.Join("&", pairs);
    }

    private static string FillPath(Operation operation, Dictionary<string, object?> parameters)
    {
        var path = operation.Path;
        foreach (var name in operation.PathParams)
        {
            if (!parameters.TryGetValue(name, out var value) || value is null)
            {
                throw new ArgumentException($"Missing path parameter '{name}' for {operation.Key}");
            }
            path = path.Replace("{" + name + "}", Uri.EscapeDataString(Format(value)));
            parameters.Remove(name);
        }
        return path;
    }

    private static bool BelongsInBody(Operation operation, string name) =>
        operation.HasBody && (operation.Body.Contains(name) || !operation.Query.Contains(name));

    private static string Pair(string key, object? value) => $"{Uri.EscapeDataString(key)}={Uri.EscapeDataString(Format(value))}";

    private static string Format(object? value) => value switch
    {
        bool flag => flag ? "true" : "false",
        IFormattable formattable => formattable.ToString(null, CultureInfo.InvariantCulture),
        _ => Convert.ToString(value, CultureInfo.InvariantCulture) ?? "",
    };

    public static Dictionary<string, object?> ToDictionary(object? parameters)
    {
        switch (parameters)
        {
            case null:
                return new();
            case IDictionary<string, object?> typed:
                return new Dictionary<string, object?>(typed);
            case IDictionary legacy:
                return legacy.Cast<DictionaryEntry>().ToDictionary(entry => (string)entry.Key, entry => entry.Value);
            default:
                return parameters.GetType().GetProperties(BindingFlags.Public | BindingFlags.Instance)
                    .ToDictionary(property => property.Name, property => property.GetValue(parameters), StringComparer.Ordinal);
        }
    }
}
