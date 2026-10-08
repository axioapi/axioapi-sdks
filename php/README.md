# axioapi for PHP

PHP client for the [AxioAPI](https://axioapi.com) REST API: temp mail API, receive SMS and OTP API, email validation API, proxy API, SEO API (keyword data and backlink API) and social scraper APIs. One API key, pay per request. Requires PHP 8.1+ with `ext-curl`.

```bash
composer require axioapi/axioapi
export AXIOAPI_KEY=ak_...
```

```php
use AxioAPI\Client;
use AxioAPI\InsufficientCreditsException;
use AxioAPI\RateLimitException;

$client = new Client();            // or new Client('ak_...')

// Keyword data API: volume, CPC and competition for up to 10 keywords per request
$rows = $client->seo->keywordMetrics(['keywords' => ['api gateway', 'proxy scraper'], 'country' => 'us']);

// Backlink API: summary with every data source that answered
$backlinks = $client->seo->backlinksSummary(['domain' => 'example.com']);
var_dump($backlinks['partial'], $backlinks['missing'], array_keys($backlinks['sources']));

// Temp mail API: one inbox per test
$inbox = $client->temporaryEmail->createInbox(['ttl_minutes' => 10]);

try {
    $client->verify->wait(['number' => '+12025550192']);
} catch (RateLimitException $e) {
    echo "retry in {$e->retryAfter}s, request {$e->requestId}";
} catch (InsufficientCreditsException $e) {
    echo 'top up credits';
}
```

- `$client->group->operation([...])` for every endpoint (camelCase, snake_case or kebab-case); `$client->call('seo.keyword-metrics', [...])` works by capability key. `$client->operations()` lists all of them with method, path and credit cost.
- Returns the `data` field. `$client->request('GET', '/api/v1/account/limits', raw: true)` returns the whole envelope.
- Image and audio endpoints return the raw bytes as a string.
- Options: `new Client($key, ['base_url' => ..., 'timeout' => 30.0, 'max_retries' => 2])`. 429 is retried for all methods; 502/503/504 and network errors only for GET and DELETE.
- Errors extend `AxioAPI\AxioAPIException` (`status`, `errorCode`, `requestId`, `fields`).

Docs: https://axioapi.com/docs · Guides: https://axioapi.com/guides · License: MIT
