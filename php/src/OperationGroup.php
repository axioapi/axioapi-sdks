<?php

declare(strict_types=1);

namespace AxioAPI;

use BadMethodCallException;

/** `$client->seo`: method-call access to the operations of one API group. */
final class OperationGroup
{
    public function __construct(private readonly Client $client, private readonly string $group) {}

    /** @param list<mixed> $arguments a single array of parameters */
    public function __call(string $name, array $arguments): mixed
    {
        $key = $this->client->registry->resolve($this->group, $name);
        if ($key === null) {
            throw new BadMethodCallException("AxioAPI has no operation '{$this->group}.{$name}'.");
        }

        return $this->client->call($key, $arguments[0] ?? []);
    }
}
