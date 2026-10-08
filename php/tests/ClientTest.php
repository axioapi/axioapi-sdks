<?php

declare(strict_types=1);

namespace AxioAPI\Tests;

use AxioAPI\Client;
use AxioAPI\Exception\AuthenticationException;
use AxioAPI\Exception\AxioAPIException;
use AxioAPI\Exception\ConnectionException;
use AxioAPI\Exception\InsufficientCreditsException;
use AxioAPI\Exception\NotFoundException;
use AxioAPI\Exception\RateLimitException;
use AxioAPI\Exception\ValidationException;
use PHPUnit\Framework\TestCase;

final class ClientTest extends TestCase
{
    private function url(): string
    {
        return getenv('AXIOAPI_MOCK_URL') ?: 'http://127.0.0.1:8765';
    }

    private function client(array $options = [], string $key = 'test_key'): Client
    {
        return new Client($key, $options + ['base_url' => $this->url(), 'sleep' => static function (float $s): void {}]);
    }

    protected function setUp(): void
    {
        file_get_contents($this->url().'/__reset');
    }

    private function hits(): array
    {
        return json_decode((string) file_get_contents($this->url().'/__hits'), true);
    }

    public function test_requires_key(): void
    {
        putenv('AXIOAPI_KEY');
        $this->expectException(\InvalidArgumentException::class);
        new Client(null, ['base_url' => $this->url()]);
    }

    public function test_group_call_sends_json_body_and_unwraps_data(): void
    {
        $data = $this->client()->seo->keywordMetrics(['keywords' => ['api gateway', 'proxy scraper'], 'country' => 'us']);
        $this->assertSame('POST', $data['method']);
        $this->assertSame('/api/v1/seo/keywords/metrics', $data['path']);
        $this->assertSame(['keywords' => ['api gateway', 'proxy scraper'], 'country' => 'us'], $data['body']);
        $this->assertSame('application/json', $data['content_type']);
        $this->assertStringStartsWith('axioapi-php/', $data['user_agent']);
    }

    public function test_snake_and_kebab_names_resolve(): void
    {
        $c = $this->client();
        $this->assertSame('/api/v1/seo/keywords/metrics', $c->seo->keyword_metrics(['keywords' => ['a']])['path']);
        $this->assertSame('/api/v1/seo/keywords/metrics', $c->call('seo.keyword-metrics', ['keywords' => ['a']])['path']);
    }

    public function test_path_params_are_encoded_and_query_separated(): void
    {
        $c = $this->client();
        $this->assertSame('/api/v1/seo/domains/exa%20mple.com/backlinks', $c->call('seo.backlinks-summary', ['domain' => 'exa mple.com'])['path']);
        $data = $c->seo->keywordSuggestions(['q' => 'laravel api', 'country' => 'us', 'lang' => 'en', 'skipped' => null]);
        $this->assertSame(['q' => ['laravel api'], 'country' => ['us'], 'lang' => ['en']], $data['query']);
    }

    public function test_missing_path_param_unknown_operation_and_group(): void
    {
        $c = $this->client();
        try {
            $c->call('seo.backlinks-summary');
            $this->fail('expected exception');
        } catch (\InvalidArgumentException $e) {
            $this->assertStringContainsString('Missing path parameter', $e->getMessage());
        }
        $this->expectException(\InvalidArgumentException::class);
        $c->call('nope.nothing');
    }

    public function test_unknown_group_and_method_throw(): void
    {
        $c = $this->client();
        $this->assertFalse(isset($c->nothing));
        $this->assertTrue(isset($c->seo));
        try {
            $c->nothing;
            $this->fail('expected exception');
        } catch (\OutOfBoundsException) {
            $this->addToAssertionCount(1);
        }
        $this->expectException(\BadMethodCallException::class);
        $c->seo->doesNotExist();
    }

    public function test_every_registry_operation_is_reachable(): void
    {
        $c = $this->client();
        foreach (array_keys($c->operations()) as $key) {
            [$group, $rest] = explode('.', $key, 2);
            $this->assertNotNull($c->registry->resolve($group, $rest), $key);
        }
    }

    public function test_raw_envelope(): void
    {
        $env = $this->client()->request('GET', '/api/v1/account/limits', raw: true);
        $this->assertSame('success', $env['status']);
        $this->assertSame('req_test_1', $env['request']['id']);
    }

    public function test_binary_download_returns_string(): void
    {
        $body = $this->client()->call('ai-image.artifact', ['job' => 'abc']);
        $this->assertIsString($body);
        $this->assertStringStartsWith("\x89PNG", $body);
    }

    public function test_errors_map_to_classes(): void
    {
        try {
            $this->client([], 'wrong')->account->limits();
            $this->fail('expected exception');
        } catch (AuthenticationException $e) {
            $this->assertSame(401, $e->status);
            $this->assertSame('req_test_1', $e->requestId);
        }
        try {
            $this->client([], 'nocredit')->account->limits();
            $this->fail('expected exception');
        } catch (InsufficientCreditsException) {
            $this->addToAssertionCount(1);
        }
        try {
            $this->client()->request('GET', '/api/v1/temp-mail/inboxes/missing');
            $this->fail('expected exception');
        } catch (NotFoundException) {
            $this->addToAssertionCount(1);
        }
        try {
            $this->client()->seo->onPageAudit();
            $this->fail('expected exception');
        } catch (ValidationException $e) {
            $this->assertSame(['url' => ['The url field is required.']], $e->fields);
        }
        try {
            $this->client()->request('GET', '/api/v1/boom');
            $this->fail('expected exception');
        } catch (AxioAPIException $e) {
            $this->assertSame(500, $e->status);
        }
    }

    public function test_retries_idempotent503_then_succeeds(): void
    {
        $this->assertSame(3, $this->client()->request('GET', '/api/v1/flaky')['attempts']);
    }

    public function test_retries429_gives_up_with_retry_after_and_post_is_not_retried_on503(): void
    {
        $this->assertSame(2, $this->client()->request('GET', '/api/v1/ratelimited')['attempts']);
        try {
            $this->client(['max_retries' => 1])->request('POST', '/api/v1/always429', [], []);
            $this->fail('expected exception');
        } catch (RateLimitException $e) {
            $this->assertSame(7.0, $e->retryAfter);
        }
        $this->assertSame(2, $this->hits()['always429']);
        try {
            $this->client()->request('POST', '/api/v1/post503', [], []);
            $this->fail('expected exception');
        } catch (AxioAPIException) {
            $this->addToAssertionCount(1);
        }
        $this->assertSame(1, $this->hits()['post503']);
    }

    public function test_connection_error(): void
    {
        $this->expectException(ConnectionException::class);
        (new Client('k', ['base_url' => 'http://127.0.0.1:1', 'max_retries' => 0, 'timeout' => 2.0]))->account->limits();
    }
}
