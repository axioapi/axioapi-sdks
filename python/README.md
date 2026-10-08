# axioapi for Python

Python client for the [AxioAPI](https://axioapi.com) REST API: temp mail API, receive SMS and OTP API, email validation API, proxy API, SEO API (keyword data and backlink API) and social scraper APIs. One API key, pay per request. No dependencies.

```bash
pip install axioapi
export AXIOAPI_KEY=ak_...
```

```python
from axioapi import AxioAPI, RateLimitError, InsufficientCreditsError

client = AxioAPI()  # or AxioAPI("ak_...")

# Keyword data API: volume, CPC and competition for up to 10 keywords per request
rows = client.seo.keyword_metrics(keywords=["api gateway", "proxy scraper"], country="us")

# Backlink API: summary with every data source that answered
backlinks = client.seo.backlinks_summary(domain="example.com")
print(backlinks["partial"], backlinks["missing"], list(backlinks["sources"]))

# Temp mail API: one inbox per test
inbox = client.temporary_email.create_inbox(ttl_minutes=10)
messages = client.temporary_email.list_messages(inbox=inbox["id"])

try:
    client.verify.wait(number="+12025550192")
except RateLimitError as e:
    print("retry in", e.retry_after, "request", e.request_id)
except InsufficientCreditsError:
    print("top up credits")
```

- `client.<group>.<operation>(**params)` for every endpoint; `client.call("seo.keyword-metrics", ...)` works by capability key. `client.operations()` lists all of them with method, path and credit cost.
- Returns the `data` field. `client.request("GET", "/api/v1/account/limits", raw=True)` returns the whole envelope.
- Image and audio endpoints return `bytes`.
- Options: `AxioAPI(api_key, base_url=..., timeout=30, max_retries=2)`. 429 is retried for all methods; 502/503/504 and network errors only for GET and DELETE.

Docs: https://axioapi.com/docs · Guides: https://axioapi.com/guides · License: MIT
