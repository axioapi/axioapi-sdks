using System.Text.Json;

namespace AxioAPI;

/// <summary><c>client.Group("seo")</c>: call an operation of one API group by name.</summary>
public sealed class OperationGroup
{
    private readonly AxioApiClient _client;
    private readonly string _group;

    internal OperationGroup(AxioApiClient client, string group)
    {
        _client = client;
        _group = group;
    }

    public Task<JsonElement> CallAsync(string name, object? parameters = null, CancellationToken cancellationToken = default) =>
        _client.CallAsync(KeyOf(name), parameters, cancellationToken);

    public Task<byte[]> CallBinaryAsync(string name, object? parameters = null, CancellationToken cancellationToken = default) =>
        _client.CallBinaryAsync(KeyOf(name), parameters, cancellationToken);

    private string KeyOf(string name) =>
        _client.Resolve(_group, name) ?? throw new ArgumentException($"AxioAPI has no operation '{_group}.{name}'.");
}
