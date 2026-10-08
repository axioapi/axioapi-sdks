<?php

declare(strict_types=1);

namespace AxioAPI\Exception;

/** 429: request limit reached; $retryAfter is in seconds when the server sent it. */
class RateLimitException extends AxioAPIException
{
    public ?float $retryAfter = null;
}
