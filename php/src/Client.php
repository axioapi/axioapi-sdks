<?php

declare(strict_types=1);

namespace AxioAPI;

use AxioAPI\Exception\ConnectionException;
use AxioAPI\Http\CurlTransport;
use AxioAPI\Http\RequestBuilder;
use AxioAPI\Http\ResponseParser;
use AxioAPI\Http\RetryPolicy;
use AxioAPI\Registry\Operation;
use AxioAPI\Registry\Registry;
use InvalidArgumentException;
use OutOfBoundsException;

/**
 * AxioAPI client.
 *
 *     $client = new \AxioAPI\Client('ak_...');
 *     $client->seo->keywordMetrics(['keywords' => ['api gateway'], 'country' => 'us']);
 */
final class Client
{
    public const VERSION = '1.0.0';

    private const DEFAULT_BASE_URL = 'https://axioapi.com';

    public readonly string $apiKey;

    public readonly string $baseUrl;

    public readonly Registry $registry;

    private readonly string $userAgent;

    private readonly RequestBuilder $requests;

    private readonly ResponseParser $responses;

    private readonly RetryPolicy $retry;

    private readonly CurlTransport $transport;

    /** @param array{base_url?: string, timeout?: float, max_retries?: int, user_agent?: string, sleep?: callable} $options */
    public function __construct(?string $apiKey = null, array $options = [])
    {
        $key = $apiKey ?? (getenv('AXIOAPI_KEY') ?: '');
        if ($key === '') {
            throw new InvalidArgumentException('Pass an API key or set the AXIOAPI_KEY environment variable.');
        }
        $this->apiKey = $key;
        $this->baseUrl = rtrim($options['base_url'] ?? (getenv('AXIOAPI_BASE_URL') ?: self::DEFAULT_BASE_URL), '/');
        $this->userAgent = $options['user_agent'] ?? 'axioapi-php/'.self::VERSION;
        $this->registry = Registry::load();
        $this->requests = new RequestBuilder;
        $this->responses = new ResponseParser;
        $this->retry = new RetryPolicy(max(0, (int) ($options['max_retries'] ?? 2)), $options['sleep'] ?? static fn (float $seconds) => usleep((int) ($seconds * 1_000_000)));
        $this->transport = new CurlTransport((float) ($options['timeout'] ?? 30.0));
    }

    /** @return array<string, Operation> */
    public function operations(): array
    {
        return $this->registry->all();
    }

    public function __get(string $name): OperationGroup
    {
        if (! $this->registry->hasGroup($name)) {
            throw new OutOfBoundsException("AxioAPI has no operation group '{$name}'.");
        }

        return new OperationGroup($this, $name);
    }

    public function __isset(string $name): bool
    {
        return $this->registry->hasGroup($name);
    }

    /**
     * Calls an operation by capability key (for example 'seo.keyword-metrics') and returns `data`.
     *
     * @param  array<string, mixed>  $params
     */
    public function call(string $operation, array $params = []): mixed
    {
        $found = $this->registry->find($operation);
        if ($found === null) {
            throw new InvalidArgumentException("Unknown operation '{$operation}'. See operations().");
        }
        $prepared = $this->requests->build($found, $params);

        return $this->request($prepared->method, $prepared->path, $prepared->query, $prepared->body);
    }

    /**
     * Sends a request with retries; returns `data`, the envelope when $raw, or the body string for files.
     *
     * @param  array<string, mixed>  $query
     */
    public function request(string $method, string $path, array $query = [], mixed $body = null, bool $raw = false): mixed
    {
        $method = strtoupper($method);
        $url = $this->url($path, $query);
        $payload = $body === null ? null : json_encode($body, JSON_THROW_ON_ERROR | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
        $headers = $this->headers($payload !== null);

        for ($attempt = 0; ; $attempt++) {
            try {
                $response = $this->transport->send($method, $url, $headers, $payload);
            } catch (ConnectionException $failure) {
                if ($this->retry->shouldRetryConnection($method, $attempt)) {
                    $this->retry->wait($attempt);

                    continue;
                }
                throw new ConnectionException("Could not reach {$this->baseUrl}: {$failure->getMessage()}");
            }
            if ($response->isSuccess()) {
                return $this->responses->parse($response, $raw);
            }
            if ($this->retry->shouldRetryStatus($response->status, $method, $attempt)) {
                $this->retry->wait($attempt, $response->header('retry-after'));

                continue;
            }
            throw $this->responses->error($response);
        }
    }

    /** @param array<string, mixed> $query */
    private function url(string $path, array $query): string
    {
        $url = $this->baseUrl.(str_starts_with($path, '/') ? $path : '/'.$path);

        return $query === [] ? $url : $url.'?'.RequestBuilder::encodeQuery($query);
    }

    /** @return list<string> */
    private function headers(bool $hasBody): array
    {
        $headers = ['Authorization: Bearer '.$this->apiKey, 'Accept: application/json', 'User-Agent: '.$this->userAgent];
        if ($hasBody) {
            $headers[] = 'Content-Type: application/json';
        }

        return $headers;
    }
}
