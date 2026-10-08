using System.Text.RegularExpressions;

namespace AxioAPI;

internal static class Naming
{
    /// <summary>Folds snake_case, camelCase and kebab-case to one comparable form.</summary>
    public static string Normalize(string name) => Regex.Replace(name.ToLowerInvariant(), "[^a-z0-9]", "");
}
