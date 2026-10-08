# AxioAPI Ruby SDK: temp mail, SMS OTP, email validation, proxy, SEO and backlink API

Ruby client for the [AxioAPI](https://axioapi.com) REST API: temp mail API, receive SMS and OTP API, email validation API, proxy API, SEO API (keyword data and backlink API) and social scraper APIs. One API key, pay per request. Standard library only. Ruby 2.7+.

<!-- start:begin -->
## Get started in 3 steps

### 1. Get an API key

[Create a free account](https://axioapi.com/portal/register), then open [API keys](https://axioapi.com/account/tokens), create a key and copy it. New accounts receive free credits after verification, enough to try every endpoint.

Set it as an environment variable (the SDK reads `AXIOAPI_KEY`):

```bash
export AXIOAPI_KEY=ak_your_key        # macOS / Linux
```

```powershell
$env:AXIOAPI_KEY = "ak_your_key"      # Windows PowerShell
```

### 2. Install

```ruby
# Gemfile
gem 'axioapi', git: 'https://github.com/axioapi/axioapi-ruby.git'
```

Then `bundle install`. Requires Ruby 2.7+. Standard library only. Once released on RubyGems: `gem install axioapi`.

### 3. Make your first call

```ruby
require 'axioapi'

client = AxioAPI::Client.new            # reads AXIOAPI_KEY
p client.account.limits
p client.seo.keyword_metrics(keywords: ['api gateway'], country: 'us')
```

Every endpoint works the same way: `client.<group>.<operation>(params)`. See the examples below and the [full reference](https://axioapi.com/docs).
<!-- start:end -->

## More examples

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

<!-- seo:start -->
## What you can build with the Ruby SDK

| Use case | API page | Typical call |
|---|---|---|
| [Temp mail API](https://axioapi.com/apis/temporary-email): disposable inboxes for signup and password-reset tests | `temporary-email` | create inbox, list messages, read message |
| [Receive SMS API](https://axioapi.com/apis/sms) and [OTP API](https://axioapi.com/apis/verify): public numbers and "wait for the code" | `sms`, `verify` | list numbers, wait for OTP |
| [Email validation API](https://axioapi.com/apis/email-validation): syntax, MX and disposable-address checks | `email` | validate, batch validate |
| [Proxy API](https://axioapi.com/apis/proxy-vpn): sticky and rotating proxy sessions by country | `proxy` | create session, rotate, close |
| [SEO API](https://axioapi.com/apis/seo): keyword data API (volume, CPC), backlink API, domain overview and history, on-page audit | `seo` | keyword metrics, backlinks summary |
| [TikTok](https://axioapi.com/apis/social-tiktok), [Facebook](https://axioapi.com/apis/social-facebook), [Instagram](https://axioapi.com/apis/social-instagram), [YouTube](https://axioapi.com/apis/social-youtube), [X (Twitter)](https://axioapi.com/apis/social-twitter), [LinkedIn](https://axioapi.com/apis/social-linkedin) and [Reddit](https://axioapi.com/apis/social-reddit) scraper APIs | `social` | profiles, posts, comments, transcripts |

## Guides with working code

- [Backlink API: check a domain's backlinks in Python](https://axioapi.com/guides/backlink-api-check-domain-python)
- [Keyword data API: get search volume and CPC in code](https://axioapi.com/guides/keyword-data-api-volume-cpc)
- [SEO report API: build a domain ranking report](https://axioapi.com/guides/seo-report-api-domain-ranking)
- [Test signup emails with Playwright and a temp mail API](https://axioapi.com/guides/playwright-temp-mail-signup-test)
- [Test OTP flows with a receive SMS API](https://axioapi.com/guides/otp-testing-with-sms-api)
- [Sticky vs rotating proxy: which one to use](https://axioapi.com/guides/sticky-vs-rotating-proxy)

Free tools that need no account: [backlink checker](https://axioapi.com/tools/backlink-checker), [email validator](https://axioapi.com/tools/email-validator), [DNS lookup](https://axioapi.com/tools/dns-lookup), [BIN checker](https://axioapi.com/tools/bin-checker).

## FAQ

**Is there a Ruby client for the AxioAPI backlink API and keyword data API?** Yes, this package. `seo.backlinks_summary` returns the backlink summary of a domain with the figures of every data source, and `seo.keyword_metrics` returns search volume, CPC and competition for up to 10 keywords per request.

**How do I test signup emails and OTP codes from Ruby?** Create a disposable inbox with the temp mail API, submit its address in your form and read the message. For SMS codes, call the verify endpoint, which waits for the OTP on a public number and returns it.

**How much does it cost?** You pay per request with credits, and each endpoint lists its price on its [API page](https://axioapi.com/apis) and in the OpenAPI spec (`x-credit-cost`). New accounts receive free credits after verification. See [pricing](https://axioapi.com/pricing).

**Where is the full reference?** [axioapi.com/docs](https://axioapi.com/docs), the [OpenAPI 3.1 spec](https://axioapi.com/api/v1/openapi.json) and [llms.txt](https://axioapi.com/llms.txt) for AI agents.

Vietnamese: [AxioAPI tiếng Việt](https://axioapi.com/vi), [API SEO và API backlink](https://axioapi.com/vi/apis/seo).
<!-- seo:end -->
