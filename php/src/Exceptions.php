<?php

declare(strict_types=1);

namespace AxioAPI;

/** Base class for every API error; carries the HTTP status, error code and request_id for support. */
class AxioAPIException extends \RuntimeException
{
    /**
     * @param array<string, list<string>> $fields
     */
    public function __construct(
        string $message,
        public readonly ?int $status = null,
        public readonly ?string $errorCode = null,
        public readonly ?string $requestId = null,
        public readonly array $fields = [],
        public readonly mixed $body = null,
    ) {
        parent::__construct($message, $status ?? 0);
    }
}

/** 401: the API key is missing or invalid. */
class AuthenticationException extends AxioAPIException
{
}

/** 402: not enough credits for this call. */
class InsufficientCreditsException extends AxioAPIException
{
}

/** 404: the resource does not exist, expired or is not yours. */
class NotFoundException extends AxioAPIException
{
}

/** 422: invalid parameters; see $fields. */
class ValidationException extends AxioAPIException
{
}

/** The request never produced an HTTP response (DNS, TLS, timeout). */
class ConnectionException extends AxioAPIException
{
}

/** 429: request limit reached; $retryAfter is in seconds when the server sent it. */
class RateLimitException extends AxioAPIException
{
    public ?float $retryAfter = null;
}
