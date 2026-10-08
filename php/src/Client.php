<?php

declare(strict_types=1);

namespace AxioAPI;

/**
 * AxioAPI client.
 *
 *     $client = new \AxioAPI\Client('ak_...');
 *     $client->seo->keywordMetrics(['keywords' => ['api gateway'], 'country' => 'us']);
 *
 * @property-read OperationGroup $seo
 */
final class Client
{
    public const VERSION = '1.0.0';
    private const DEFAULT_BASE_URL = 'https://axioapi.com';

    public readonly string $apiKey;
    public readonly string $baseUrl;
    /** @var array<string, array<string, mixed>> */
    private array $operations;
    /** @var array<string, string> normalized "group.name" => operation key */
    private array $index = [];
    /** @var array<string, bool> */
    private array $groups = [];
    /** @var callable(float): void */
    private $sleep;

    /**
     * @param array{base_url?: string, timeout?: float, max_retries?: int, user_agent?: string, sleep?: callable} $options
     */
    public function __construct(?string $apiKey = null, private array $options = [])
    {
        $key = $apiKey ?? (getenv('AXIOAPI_KEY') ?: '');
        if ($key === '') {
            throw new \InvalidArgumentException('Pass an API key or set the AXIOAPI_KEY environment variable.');
        }
        $this->apiKey = $key;
        $this->baseUrl = rtrim($options['base_url'] ?? (getenv('AXIOAPI_BASE_URL') ?: self::DEFAULT_BASE_URL), '/');
        $this->sleep = $options['sleep'] ?? static function (float $seconds): void {
            usleep((int) ($seconds * 1_000_000));
        };
        $json = json_decode((string) file_get_contents(__DIR__.'/operations.json'), true, 512, JSON_THROW_ON_ERROR);
        $this->operations = $json['operations'];
        foreach (array_keys($this->operations) as $opKey) {
            [$group, $rest] = array_pad(explode('.', $opKey, 2), 2, '');
            $this->groups[self::normalize($group)] = true;
            $this->index[self::normalize($group).'.'.self::normalize($rest)] = $opKey;
        }
    }

    /** @return array<string, array<string, mixed>> every operation: method, path, parameters, credit cost */
    public function operations(): array
    {
        return $this->operations;
    }

    public function __get(string $name): OperationGroup
    {
        $group = self::normalize($name);
        if (! isset($this->groups[$group])) {
            throw new \OutOfBoundsException("AxioAPI has no operation group '{$name}'.");
        }

        return new OperationGroup($this, $group);
    }

    public function __isset(string $name): bool
    {
        return isset($this->groups[self::normalize($name)]);
    }

    /** @internal */
    public function resolve(string $group, string $name): ?string
    {
        return $this->index[self::normalize($group).'.'.self::normalize($name)] ?? null;
    }

    /**
     * Call an operation by capability key, e.g. call('seo.keyword-metrics', ['keywords' => [...]]). Returns `data`.
     *
     * @param array<string, mixed> $params
     */
    public function call(string $operation, array $params = []): mixed
    {
        $key = isset($this->operations[$operation]) ? $operation : null;
        if ($key === null && str_contains($operation, '.')) {
            [$group, $rest] = explode('.', $operation, 2);
            $key = $this->resolve($group, $rest);
        }
        if ($key === null) {
            throw new \InvalidArgumentException("Unknown operation '{$operation}'. See operations().");
        }
        $op = $this->operations[$key];
        $path = $op['path'];
        foreach ($op['path_params'] as $name) {
            if (! array_key_exists($name, $params)) {
                throw new \InvalidArgumentException("Missing path parameter '{$name}' for {$key}");
            }
            $path = str_replace('{'.$name.'}', rawurlencode((string) $params[$name]), $path);
            unset($params[$name]);
        }
        $hasBody = ! in_array($op['method'], ['GET', 'DELETE', 'HEAD'], true);
        $query = [];
        $body = [];
        foreach ($params as $name => $value) {
            if ($value === null) {
                continue;
            }
            if ($hasBody && (in_array($name, $op['body'], true) || ! in_array($name, $op['query'], true))) {
                $body[$name] = $value;
            } else {
                $query[$name] = $value;
            }
        }

        return $this->request($op['method'], $path, $query, $hasBody && $body !== [] ? $body : null);
    }

    /**
     * Send a request. Returns `data`, the whole envelope when $raw is true, or the raw string for file downloads.
     *
     * @param array<string, mixed> $query
     */
    public function request(string $method, string $path, array $query = [], mixed $body = null, bool $raw = false): mixed
    {
        $method = strtoupper($method);
        $url = $this->baseUrl.(str_starts_with($path, '/') ? $path : '/'.$path);
        if ($query !== []) {
            $url .= '?'.self::buildQuery($query);
        }
        $headers = [
            'Authorization: Bearer '.$this->apiKey,
            'Accept: application/json',
            'User-Agent: '.($this->options['user_agent'] ?? 'axioapi-php/'.self::VERSION),
        ];
        $payload = null;
        if ($body !== null) {
            $payload = json_encode($body, JSON_THROW_ON_ERROR | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
            $headers[] = 'Content-Type: application/json';
        }
        $maxRetries = max(0, (int) ($this->options['max_retries'] ?? 2));
        $idempotent = in_array($method, ['GET', 'HEAD', 'DELETE'], true);

        for ($attempt = 0;; $attempt++) {
            $ch = curl_init($url);
            curl_setopt_array($ch, [
                CURLOPT_CUSTOMREQUEST => $method,
                CURLOPT_HTTPHEADER => $headers,
                CURLOPT_RETURNTRANSFER => true,
                CURLOPT_HEADER => true,
                CURLOPT_TIMEOUT_MS => (int) (($this->options['timeout'] ?? 30.0) * 1000),
                CURLOPT_CONNECTTIMEOUT => 10,
            ]);
            if ($payload !== null) {
                curl_setopt($ch, CURLOPT_POSTFIELDS, $payload);
            }
            $response = curl_exec($ch);
            if ($response === false) {
                $message = curl_error($ch);
                curl_close($ch);
                if ($idempotent && $attempt < $maxRetries) {
                    ($this->sleep)(self::delay(null, $attempt));

                    continue;
                }
                throw new ConnectionException("Could not reach {$this->baseUrl}: {$message}");
            }
            $status = (int) curl_getinfo($ch, CURLINFO_RESPONSE_CODE);
            $headerSize = (int) curl_getinfo($ch, CURLINFO_HEADER_SIZE);
            curl_close($ch);
            $rawHeaders = self::parseHeaders(substr($response, 0, $headerSize));
            $content = substr($response, $headerSize);

            if ($status >= 200 && $status < 300) {
                return $this->parse($rawHeaders, $content, $raw);
            }
            $retryable = $status === 429 || ($idempotent && in_array($status, [502, 503, 504], true));
            if ($retryable && $attempt < $maxRetries) {
                ($this->sleep)(self::delay($rawHeaders['retry-after'] ?? null, $attempt));

                continue;
            }
            throw $this->error($status, $rawHeaders, $content);
        }
    }

    /** @param array<string, string> $headers */
    private function parse(array $headers, string $content, bool $raw): mixed
    {
        if (! str_contains($headers['content-type'] ?? '', 'json')) {
            return $content;
        }
        if ($content === '') {
            return null;
        }
        $envelope = json_decode($content, true, 512, JSON_THROW_ON_ERROR);

        return ($raw || ! is_array($envelope)) ? $envelope : ($envelope['data'] ?? null);
    }

    /** @param array<string, string> $headers */
    private function error(int $status, array $headers, string $content): AxioAPIException
    {
        $body = json_decode($content, true);
        $body = is_array($body) ? $body : [];
        $err = is_array($body['error'] ?? null) ? $body['error'] : [];
        $message = (string) ($err['message'] ?? $body['message'] ?? "HTTP {$status}");
        $args = [$message, $status, $err['code'] ?? null, $err['request_id'] ?? ($headers['x-request-id'] ?? null), (array) ($err['fields'] ?? []), $body ?: null];

        return match ($status) {
            401 => new AuthenticationException(...$args),
            402 => new InsufficientCreditsException(...$args),
            404 => new NotFoundException(...$args),
            422 => new ValidationException(...$args),
            429 => (function () use ($args, $headers): RateLimitException {
                $e = new RateLimitException(...$args);
                $retryAfter = $headers['retry-after'] ?? null;
                $e->retryAfter = ($retryAfter !== null && ctype_digit($retryAfter)) ? (float) $retryAfter : null;

                return $e;
            })(),
            default => new AxioAPIException(...$args),
        };
    }

    /** @return array<string, string> lower-cased header names of the last response block */
    private static function parseHeaders(string $block): array
    {
        $blocks = array_values(array_filter(preg_split("/\r\n\r\n/", trim($block)) ?: []));
        $last = $blocks ? (string) end($blocks) : '';
        $out = [];
        foreach (explode("\r\n", $last) as $line) {
            if (str_contains($line, ':')) {
                [$name, $value] = explode(':', $line, 2);
                $out[strtolower(trim($name))] = trim($value);
            }
        }

        return $out;
    }

    /** @param array<string, mixed> $params */
    private static function buildQuery(array $params): string
    {
        $pairs = [];
        foreach ($params as $key => $value) {
            if ($value === null) {
                continue;
            }
            if (is_array($value)) {
                foreach ($value as $item) {
                    $pairs[] = rawurlencode($key.'[]').'='.rawurlencode((string) $item);
                }
            } else {
                $pairs[] = rawurlencode((string) $key).'='.rawurlencode(is_bool($value) ? ($value ? 'true' : 'false') : (string) $value);
            }
        }

        return implode('&', $pairs);
    }

    private static function delay(?string $retryAfter, int $attempt): float
    {
        if ($retryAfter !== null && is_numeric($retryAfter)) {
            return min((float) $retryAfter, 30.0);
        }

        return min(0.5 * (2 ** $attempt), 8.0);
    }

    private static function normalize(string $name): string
    {
        return preg_replace('/[^a-z0-9]/', '', strtolower($name)) ?? '';
    }
}
