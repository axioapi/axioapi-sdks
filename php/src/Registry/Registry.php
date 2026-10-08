<?php

declare(strict_types=1);

namespace AxioAPI\Registry;

use AxioAPI\Naming;
use JsonException;

/** Every API operation, loaded from operations.json. */
final class Registry
{
    /** @var array<string, Operation> */
    private array $operations = [];

    /** @var array<string, string> normalized "group.name" => operation key */
    private array $index = [];

    /** @var array<string, true> */
    private array $groups = [];

    /** @throws JsonException */
    public static function load(?string $file = null): self
    {
        $data = json_decode((string) file_get_contents($file ?? __DIR__.'/../operations.json'), true, 512, JSON_THROW_ON_ERROR);
        $registry = new self;
        foreach ($data['operations'] as $key => $entry) {
            $registry->add(Operation::fromArray($key, $entry));
        }

        return $registry;
    }

    /** @return array<string, Operation> */
    public function all(): array
    {
        return $this->operations;
    }

    public function hasGroup(string $group): bool
    {
        return isset($this->groups[Naming::normalize($group)]);
    }

    /** Capability key for a group and operation name in any spelling, or null. */
    public function resolve(string $group, string $name): ?string
    {
        return $this->index[self::indexKey($group, $name)] ?? null;
    }

    /** Looks an operation up by exact key or by any spelling of group.name. */
    public function find(string $operation): ?Operation
    {
        if (isset($this->operations[$operation])) {
            return $this->operations[$operation];
        }
        if (! str_contains($operation, '.')) {
            return null;
        }
        [$group, $name] = explode('.', $operation, 2);
        $key = $this->resolve($group, $name);

        return $key === null ? null : $this->operations[$key];
    }

    private function add(Operation $operation): void
    {
        [$group, $name] = explode('.', $operation->key, 2);
        $this->operations[$operation->key] = $operation;
        $this->groups[Naming::normalize($group)] = true;
        $this->index[self::indexKey($group, $name)] = $operation->key;
    }

    private static function indexKey(string $group, string $name): string
    {
        return Naming::normalize($group).'.'.Naming::normalize($name);
    }
}
