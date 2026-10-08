<?php

declare(strict_types=1);

namespace AxioAPI\Http;

final class HttpResponse
{
    /** @param array<string, string> $headers lower-cased header names */
    public function __construct(
        public readonly int $status,
        public readonly array $headers,
        public readonly string $body,
    ) {}

    public function header(string $name): ?string
    {
        return $this->headers[strtolower($name)] ?? null;
    }

    public function isSuccess(): bool
    {
        return $this->status >= 200 && $this->status < 300;
    }

    public function isJson(): bool
    {
        return str_contains($this->header('content-type') ?? '', 'json');
    }
}
