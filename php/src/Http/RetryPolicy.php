<?php

declare(strict_types=1);

namespace AxioAPI\Http;

/** 429 is retried for every method; 5xx and network errors only for idempotent ones. */
final class RetryPolicy
{
    private const RETRYABLE_STATUS = [502, 503, 504];

    private const IDEMPOTENT_METHODS = ['GET', 'HEAD', 'DELETE'];

    private const MAX_DELAY_SECONDS = 8.0;

    private const MAX_RETRY_AFTER_SECONDS = 30.0;

    /** @var callable(float): void */
    private $sleep;

    /** @param callable(float): void $sleep */
    public function __construct(public readonly int $maxRetries, callable $sleep)
    {
        $this->sleep = $sleep;
    }

    public function shouldRetryStatus(int $status, string $method, int $attempt): bool
    {
        if ($attempt >= $this->maxRetries) {
            return false;
        }

        return $status === 429 || (in_array($method, self::IDEMPOTENT_METHODS, true) && in_array($status, self::RETRYABLE_STATUS, true));
    }

    public function shouldRetryConnection(string $method, int $attempt): bool
    {
        return $attempt < $this->maxRetries && in_array($method, self::IDEMPOTENT_METHODS, true);
    }

    public function wait(int $attempt, ?string $retryAfter = null): void
    {
        ($this->sleep)($this->delay($attempt, $retryAfter));
    }

    private function delay(int $attempt, ?string $retryAfter): float
    {
        if ($retryAfter !== null && is_numeric($retryAfter)) {
            return min((float) $retryAfter, self::MAX_RETRY_AFTER_SECONDS);
        }

        return min(0.5 * (2 ** $attempt), self::MAX_DELAY_SECONDS);
    }
}
