<?php

declare(strict_types=1);

namespace AxioAPI\Http;

use AxioAPI\Registry\Operation;
use InvalidArgumentException;

/** Splits flat params into path, query and body according to the operation. */
final class RequestBuilder
{
    /** @param array<string, mixed> $params */
    public function build(Operation $operation, array $params): PreparedRequest
    {
        $path = $this->fillPath($operation, $params);
        $query = [];
        $body = [];
        foreach ($params as $name => $value) {
            if ($value === null) {
                continue;
            }
            if ($this->belongsInBody($operation, (string) $name)) {
                $body[$name] = $value;
            } else {
                $query[$name] = $value;
            }
        }

        return new PreparedRequest($operation->method, $path, $query, $operation->hasBody() && $body !== [] ? $body : null);
    }

    /** @param array<string, mixed> $params */
    public static function encodeQuery(array $params): string
    {
        $pairs = [];
        foreach ($params as $key => $value) {
            if (is_array($value)) {
                foreach ($value as $item) {
                    $pairs[] = rawurlencode($key.'[]').'='.rawurlencode((string) $item);
                }
            } elseif ($value !== null) {
                $pairs[] = rawurlencode((string) $key).'='.rawurlencode(is_bool($value) ? ($value ? 'true' : 'false') : (string) $value);
            }
        }

        return implode('&', $pairs);
    }

    /** @param array<string, mixed> $params */
    private function fillPath(Operation $operation, array &$params): string
    {
        $path = $operation->path;
        foreach ($operation->pathParams as $name) {
            if (! isset($params[$name])) {
                throw new InvalidArgumentException("Missing path parameter '{$name}' for {$operation->key}");
            }
            $path = str_replace('{'.$name.'}', rawurlencode((string) $params[$name]), $path);
            unset($params[$name]);
        }

        return $path;
    }

    private function belongsInBody(Operation $operation, string $name): bool
    {
        return $operation->hasBody() && (in_array($name, $operation->body, true) || ! in_array($name, $operation->query, true));
    }
}
