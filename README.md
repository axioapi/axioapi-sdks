# AxioAPI SDKs

Official client libraries for the [AxioAPI](https://axioapi.com) REST API: **temp mail API**, **receive SMS and OTP API**, **email validation API**, **proxy API**, **SEO API** (keyword data and backlink API) and social data scrapers (TikTok, Facebook, Instagram, YouTube, X, LinkedIn, Reddit, GitHub). One API key, pay per request.

| Language | Package | Folder | Install |
|---|---|---|---|
| Python 3.9+ | `axioapi` | [python](python) | `pip install axioapi` |
| Node.js 18+ | `axioapi` | [node](node) | `npm install axioapi` |
| PHP 8.1+ | `axioapi/axioapi` | [php](php) | `composer require axioapi/axioapi` |
| Go 1.21+ | `github.com/axioapi/axioapi-go` | [go](go) | `go get github.com/axioapi/axioapi-go` |
| Ruby 2.7+ | `axioapi` | [ruby](ruby) | `gem install axioapi` |
| Java 11+ | `com.axioapi:axioapi` | [java](java) | Maven / Gradle, see the folder README |
| C# (.NET 6+) | `AxioAPI` | [csharp](csharp) | `dotnet add package AxioAPI` |

Packages are not on the public registries yet: see [PUBLISHING.md](PUBLISHING.md). Until then install from this folder.

## Quick start

1. Create an API key in your [AxioAPI account](https://axioapi.com/portal).
2. Export it: `export AXIOAPI_KEY=ak_...`
3. Call any of the 100+ endpoints:

```python
from axioapi import AxioAPI

client = AxioAPI()  # reads AXIOAPI_KEY
rows = client.seo.keyword_metrics(keywords=["api gateway", "proxy scraper"], country="us")
inbox = client.temporary_email.create_inbox(ttl_minutes=10)
```

Every SDK has the same shape:

- **Groups and operations** mirror the API: `client.seo.keyword_metrics(...)`, `client.sms.numbers_list(...)`, `client.social.tiktok_profile(...)`. Names accept snake_case, camelCase and kebab-case. `client.call("seo.keyword-metrics", ...)` works by capability key everywhere.
- **Unwrapped data.** Successful calls return the `data` field of the response envelope. Raw envelope access is available (`raw` option or `Request`).
- **Typed errors** with `status`, `code`, `request_id` and per-field messages: authentication (401), insufficient credits (402), not found (404), validation (422), rate limit (429, with `retry_after`) and connection errors.
- **Retries.** 429 is retried for every method (honouring `Retry-After`); 502/503/504 and network errors are retried for GET and DELETE only, so a billed POST is never sent twice. Default: 2 retries.
- **Files.** Endpoints that return images or audio return bytes.
- **No heavy dependencies.** Python, Node.js, Ruby and Go use only the standard library; PHP needs `ext-curl`, Java needs Jackson, C# uses `System.Text.Json`.

All 118 operations (method, path, parameters, credit cost) live in [spec/operations.json](spec/operations.json), generated from the live OpenAPI document.

## Development

```
php sdk/tools/generate_registry.php     # rebuild spec/operations.json from the app
python sdk/tools/sync_spec.py           # copy it into every package
bash sdk/tools/test_all.sh              # run every test suite against the shared mock server
```

Every suite runs the same scenarios against `tools/mock_server.py`: JSON body and query placement, path encoding, envelope unwrapping, file downloads, all error classes, retry rules (including "POST is never retried on 5xx") and connection failures.

## License

MIT
