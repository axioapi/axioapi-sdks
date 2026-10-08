<?php

declare(strict_types=1);

namespace AxioAPI;

final class Naming
{
    /** Folds snake_case, camelCase and kebab-case to one comparable form. */
    public static function normalize(string $name): string
    {
        return preg_replace('/[^a-z0-9]/', '', strtolower($name)) ?? '';
    }
}
