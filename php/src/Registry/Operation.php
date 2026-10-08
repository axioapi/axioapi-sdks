<?php

declare(strict_types=1);

namespace AxioAPI\Registry;

final class Operation
{
    /**
     * @param  list<string>  $pathParams
     * @param  list<string>  $query
     * @param  list<string>  $body
     */
    public function __construct(
        public readonly string $key,
        public readonly string $method,
        public readonly string $path,
        public readonly string $summary,
        public readonly ?float $cost,
        public readonly array $pathParams,
        public readonly array $query,
        public readonly array $body,
        public readonly bool $binary,
    ) {}

    /** @param array<string, mixed> $data one entry of operations.json */
    public static function fromArray(string $key, array $data): self
    {
        return new self(
            $key,
            $data['method'],
            $data['path'],
            (string) ($data['summary'] ?? ''),
            isset($data['cost']) ? (float) $data['cost'] : null,
            $data['path_params'] ?? [],
            $data['query'] ?? [],
            $data['body'] ?? [],
            (bool) ($data['binary'] ?? false),
        );
    }

    public function hasBody(): bool
    {
        return ! in_array($this->method, ['GET', 'DELETE', 'HEAD'], true);
    }
}
