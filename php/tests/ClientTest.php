<?php

declare(strict_types=1);

namespace AxioAPI\Tests;

use AxioAPI\AuthenticationException;
use AxioAPI\AxioAPIException;
use AxioAPI\Client;
use AxioAPI\ConnectionException;
use AxioAPI\InsufficientCreditsException;
use AxioAPI\NotFoundException;
use AxioAPI\RateLimitException;
use AxioAPI\ValidationException;
use PHPUnit\Framework\TestCase;

final class ClientTest extends TestCase
{
    private function url(): string
    {
        return getenv('AXIOAPI_MOCK_URL') ?: 'http://127.0.0.1:8765';
    }

    private function client(array $options = [], string $key = 'test_key'): Client
    {
        return new Client($key, $options + ['base_url' => $this->url(), 'sleep' => static function (float $s): void {
        }]);
    }

    protected function setUp(): void
    {
        file_get_contents($this->url().'/__reset');
    }

    private function hits(): array
    {
        return json_decode((string) file_get_contents($this->url().'/__hits'), true);
    }

    public function testRequiresKey(): void
    {
        putenv('AXIOAPI_KEY');
        $this->expectException(\InvalidArgumentException::class);
        new Client(null, ['base_url' => $this->url()]);
    }

    public function testGroupCallSendsJsonBodyAndUnwrapsData(): void
    {
        $data = $this->client()->seo->keywordMetrics(['keywords' => ['api gateway', 'proxy scraper'], 'country' => 'us']);
        $this->assertSame('POST', $data['method']);
        $this->assertSame('/api/v1/seo/keywords/metrics', $data['path']);
        $this->assertSame(['keywords' => ['api gateway', 'proxy scraper'], 'country' => 'us'], $data['body']);
        $this->assertSame('application/json', $data['content_type']);
        $this->assertStringStartsWith('axioapi-php/', $data['user_agent']);
    }

    public function testSnakeAndKebabNamesResolve(): void
    {
        $c = $this->client();
        $this->assertSame('/api/v1/seo/keywords/metrics', $c->seo->keyword_metrics(['keywords' => ['a']])['path']);
        $this->assertSame('/api/v1/seo/keywords/metrics', $c->call('seo.keyword-metrics', ['keywords' => ['a']])['path']);
    }

    public function testPathParamsAreEncodedAndQuerySeparated(): void
    {
        $c = $this->client();
        $this->assertSame('/api/v1/seo/domains/exa%20mple.com/backlinks', $c->call('seo.backlinks-summary', ['domain' => 'exa mple.com'])['path']);
        $data = $c->seo->keywordSuggestions(['q' => 'laravel api', 'country' => 'us', 'lang' => 'en', 'skipped' => null]);
        $this->assertSame(['q' => ['laravel api'], 'country' => ['us'], 'lang' => ['en']], $data['query']);
    }

    public function testMissingPathParamUnknownOperationAndGroup(): void
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

    public function testUnknownGroupAndMethodThrow(): void
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

    public function testEveryRegistryOperationIsReachable(): void
    {
        $c = $this->client();
        foreach (array_keys($c->operations()) as $key) {
            [$group, $rest] = explode('.', $key, 2);
            $this->assertNotNull($c->resolve($group, $rest), $key);
        }
    }

    public function testRawEnvelope(): void
    {
        $env = $this->client()->request('GET', '/api/v1/account/limits', raw: true);
        $this->assertSame('success', $env['status']);
        $this->assertSame('req_test_1', $env['request']['id']);
    }

    public function testBinaryDownloadReturnsString(): void
    {
        $body = $this->client()->call('ai-image.artifact', ['job' => 'abc']);
        $this->assertIsString($body);
        $this->assertStringStartsWith("\x89PNG", $body);
    }

    public function testErrorsMapToClasses(): void
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

    public function testRetriesIdempotent503ThenSucceeds(): void
    {
        $this->assertSame(3, $this->client()->request('GET', '/api/v1/flaky')['attempts']);
    }

    public function testRetries429GivesUpWithRetryAfterAndPostIsNotRetriedOn503(): void
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

    public function testConnectionError(): void
    {
        $this->expectException(ConnectionException::class);
        (new Client('k', ['base_url' => 'http://127.0.0.1:1', 'max_retries' => 0, 'timeout' => 2.0]))->account->limits();
    }
}
