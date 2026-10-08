<?php

declare(strict_types=1);

namespace AxioAPI\Http;

final class PreparedRequest
{
    /**
     * @param  array<string, mixed>  $query
     * @param  array<string, mixed>|null  $body
     */
    public function __construct(
        public readonly string $method,
        public readonly string $path,
        public readonly array $query,
        public readonly ?array $body,
    ) {}
}
