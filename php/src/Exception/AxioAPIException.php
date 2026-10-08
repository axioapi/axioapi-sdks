<?php

declare(strict_types=1);

namespace AxioAPI\Exception;

use RuntimeException;

/** Base class for API errors; carries the HTTP status, error code and request_id for support. */
class AxioAPIException extends RuntimeException
{
    /** @param array<string, list<string>> $fields */
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
