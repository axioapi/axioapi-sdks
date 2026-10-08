<?php

declare(strict_types=1);

namespace AxioAPI;

/** `$client->seo`: method-call access to every operation of one API group. */
final class OperationGroup
{
    public function __construct(private readonly Client $client, private readonly string $group)
    {
    }

    /** @param list<mixed> $arguments  A single array of parameters. */
    public function __call(string $name, array $arguments): mixed
    {
        $key = $this->client->resolve($this->group, $name);
        if ($key === null) {
            throw new \BadMethodCallException("AxioAPI has no operation '{$this->group}.{$name}'.");
        }

        return $this->client->call($key, $arguments[0] ?? []);
    }
}
