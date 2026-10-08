<?php

declare(strict_types=1);

namespace AxioAPI\Http;

use AxioAPI\Exception\AuthenticationException;
use AxioAPI\Exception\AxioAPIException;
use AxioAPI\Exception\InsufficientCreditsException;
use AxioAPI\Exception\NotFoundException;
use AxioAPI\Exception\RateLimitException;
use AxioAPI\Exception\ValidationException;

final class ResponseParser
{
    private const ERROR_BY_STATUS = [
        401 => AuthenticationException::class,
        402 => InsufficientCreditsException::class,
        404 => NotFoundException::class,
        422 => ValidationException::class,
    ];

    /** Returns `data`, the whole envelope when $raw, or the body string for non-JSON responses (files). */
    public function parse(HttpResponse $response, bool $raw): mixed
    {
        if (! $response->isJson()) {
            return $response->body;
        }
        if ($response->body === '') {
            return null;
        }
        $envelope = json_decode($response->body, true, 512, JSON_THROW_ON_ERROR);

        return ($raw || ! is_array($envelope)) ? $envelope : ($envelope['data'] ?? null);
    }

    public function error(HttpResponse $response): AxioAPIException
    {
        $body = json_decode($response->body, true);
        $body = is_array($body) ? $body : [];
        $detail = is_array($body['error'] ?? null) ? $body['error'] : [];
        $arguments = [
            (string) ($detail['message'] ?? $body['message'] ?? "HTTP {$response->status}"),
            $response->status,
            $detail['code'] ?? null,
            $detail['request_id'] ?? $response->header('x-request-id'),
            (array) ($detail['fields'] ?? []),
            $body ?: null,
        ];

        if ($response->status === 429) {
            return $this->rateLimit($response, $arguments);
        }
        $class = self::ERROR_BY_STATUS[$response->status] ?? AxioAPIException::class;

        return new $class(...$arguments);
    }

    /** @param array<int, mixed> $arguments */
    private function rateLimit(HttpResponse $response, array $arguments): RateLimitException
    {
        $error = new RateLimitException(...$arguments);
        $retryAfter = $response->header('retry-after');
        $error->retryAfter = ($retryAfter !== null && ctype_digit($retryAfter)) ? (float) $retryAfter : null;

        return $error;
    }
}
