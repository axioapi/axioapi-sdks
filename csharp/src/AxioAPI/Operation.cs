namespace AxioAPI;

/// <summary>One API operation as described in the registry.</summary>
public sealed record Operation(
    string Key, string Method, string Path, string Summary, double? Cost,
    string[] PathParams, string[] Query, string[] Body, bool Binary)
{
    private static readonly string[] BodylessMethods = { "GET", "DELETE", "HEAD" };

    public bool HasBody => !BodylessMethods.Contains(Method);
}
