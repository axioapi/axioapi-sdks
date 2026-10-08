using System.Text.Json;

namespace AxioAPI;

/// <summary>Every API operation, loaded from the embedded operations.json.</summary>
internal sealed class Registry
{
    private const string ResourceName = "AxioAPI.operations.json";

    private readonly Dictionary<string, Operation> _operations = new();
    private readonly Dictionary<string, string> _index = new();
    private readonly HashSet<string> _groups = new();

    public static Registry Load()
    {
        using var stream = typeof(Registry).Assembly.GetManifestResourceStream(ResourceName)
            ?? throw new InvalidOperationException($"{ResourceName} resource is missing.");
        using var document = JsonDocument.Parse(stream);
        var registry = new Registry();
        foreach (var entry in document.RootElement.GetProperty("operations").EnumerateObject())
        {
            registry.Add(Parse(entry.Name, entry.Value));
        }
        return registry;
    }

    public IReadOnlyDictionary<string, Operation> All => _operations;

    public bool HasGroup(string group) => _groups.Contains(Naming.Normalize(group));

    /// <summary>Capability key for a group and operation name in any spelling, or null.</summary>
    public string? Resolve(string group, string name) => _index.GetValueOrDefault(IndexKey(group, name));

    /// <summary>Looks an operation up by exact key or by any spelling of group.name.</summary>
    public Operation? Find(string operation)
    {
        if (_operations.TryGetValue(operation, out var exact)) return exact;
        var dot = operation.IndexOf('.');
        if (dot <= 0) return null;
        var key = Resolve(operation[..dot], operation[(dot + 1)..]);
        return key is null ? null : _operations[key];
    }

    private void Add(Operation operation)
    {
        var dot = operation.Key.IndexOf('.');
        var group = operation.Key[..dot];
        _operations[operation.Key] = operation;
        _groups.Add(Naming.Normalize(group));
        _index[IndexKey(group, operation.Key[(dot + 1)..])] = operation.Key;
    }

    private static string IndexKey(string group, string name) => $"{Naming.Normalize(group)}.{Naming.Normalize(name)}";

    private static Operation Parse(string key, JsonElement node) => new(
        key,
        node.GetProperty("method").GetString()!,
        node.GetProperty("path").GetString()!,
        node.TryGetProperty("summary", out var summary) ? summary.GetString() ?? "" : "",
        node.TryGetProperty("cost", out var cost) && cost.ValueKind == JsonValueKind.Number ? cost.GetDouble() : null,
        Strings(node, "path_params"), Strings(node, "query"), Strings(node, "body"),
        node.TryGetProperty("binary", out var binary) && binary.GetBoolean());

    private static string[] Strings(JsonElement node, string property) =>
        node.TryGetProperty(property, out var array) ? array.EnumerateArray().Select(item => item.GetString()!).ToArray() : Array.Empty<string>();
}
