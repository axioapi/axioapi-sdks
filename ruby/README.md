# axioapi for Ruby

Ruby client for the [AxioAPI](https://axioapi.com) REST API: temp mail API, receive SMS and OTP API, email validation API, proxy API, SEO API (keyword data and backlink API) and social scraper APIs. One API key, pay per request. Standard library only. Ruby 2.7+.

```bash
gem install axioapi
export AXIOAPI_KEY=ak_...
```

```ruby
require 'axioapi'

client = AxioAPI::Client.new # or AxioAPI::Client.new('ak_...')

# Keyword data API: volume, CPC and competition for up to 10 keywords per request
rows = client.seo.keyword_metrics(keywords: ['api gateway', 'proxy scraper'], country: 'us')

# Backlink API: summary with every data source that answered
backlinks = client.seo.backlinks_summary(domain: 'example.com')
puts backlinks['partial'], backlinks['missing'].inspect, backlinks['sources'].keys.inspect

# Temp mail API: one inbox per test
inbox = client.temporary_email.create_inbox(ttl_minutes: 10)

begin
  client.verify.wait(number: '+12025550192')
rescue AxioAPI::RateLimitError => e
  puts "retry in #{e.retry_after}s, request #{e.request_id}"
rescue AxioAPI::InsufficientCreditsError
  puts 'top up credits'
end
```

- `client.group.operation(**params)` for every endpoint (snake_case, camelCase or kebab-case); `client.call('seo.keyword-metrics', **params)` works by capability key. `client.operations` lists all of them with method, path and credit cost.
- Returns the `data` field. `client.request('GET', '/api/v1/account/limits', raw: true)` returns the whole envelope.
- Image and audio endpoints return a binary `String`.
- Options: `AxioAPI::Client.new(key, base_url:, timeout: 30, max_retries: 2)`. 429 is retried for all methods; 502/503/504 and network errors only for GET and DELETE.
- Errors inherit from `AxioAPI::Error` (`status`, `code`, `request_id`, `fields`).

Docs: https://axioapi.com/docs · Guides: https://axioapi.com/guides · License: MIT
