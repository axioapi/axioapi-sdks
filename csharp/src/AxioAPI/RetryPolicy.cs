using System.Globalization;

namespace AxioAPI;

/// <summary>429 is retried for every method; 5xx and network errors only for idempotent ones.</summary>
internal sealed class RetryPolicy
{
    private static readonly int[] RetryableStatus = { 502, 503, 504 };
    private static readonly string[] IdempotentMethods = { "GET", "HEAD", "DELETE" };
    private const double MaxDelaySeconds = 8;
    private const double MaxRetryAfterSeconds = 30;

    private readonly int _maxRetries;
    private readonly Func<TimeSpan, CancellationToken, Task> _delay;

    public RetryPolicy(int maxRetries, Func<TimeSpan, CancellationToken, Task> delay)
    {
        _maxRetries = Math.Max(0, maxRetries);
        _delay = delay;
    }

    public bool ShouldRetryStatus(int status, string method, int attempt) =>
        attempt < _maxRetries && (status == 429 || (IdempotentMethods.Contains(method) && RetryableStatus.Contains(status)));

    public bool ShouldRetryConnection(string method, int attempt) =>
        attempt < _maxRetries && IdempotentMethods.Contains(method);

    public Task WaitAsync(int attempt, string? retryAfter, CancellationToken token) => _delay(Backoff(attempt, retryAfter), token);

    private static TimeSpan Backoff(int attempt, string? retryAfter)
    {
        if (double.TryParse(retryAfter, NumberStyles.Float, CultureInfo.InvariantCulture, out var seconds))
        {
            return TimeSpan.FromSeconds(Math.Min(seconds, MaxRetryAfterSeconds));
        }
        return TimeSpan.FromSeconds(Math.Min(0.5 * Math.Pow(2, attempt), MaxDelaySeconds));
    }
}
