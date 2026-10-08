# AxioAPI Go SDK: temp mail, SMS OTP, email validation, proxy, SEO and backlink API

Go client for the [AxioAPI](https://axioapi.com) REST API: temp mail API, receive SMS and OTP API, email validation API, proxy API, SEO API (keyword data and backlink API) and social scraper APIs. One API key, pay per request. Standard library only. Go 1.21+.

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

```bash
go get github.com/axioapi/axioapi-go@main
```

Requires Go 1.21+. Standard library only.

### 3. Make your first call

```go
package main

import (
	"context"
	"fmt"

	axioapi "github.com/axioapi/axioapi-go"
)

func main() {
	client, err := axioapi.New("") // empty reads AXIOAPI_KEY
	if err != nil {
		panic(err)
	}
	limits, err := client.Group("account").Call(context.Background(), "limits", nil)
	fmt.Println(string(limits), err)
}
```

Every endpoint works the same way: `client.<group>.<operation>(params)`. See the examples below and the [full reference](https://axioapi.com/docs).
<!-- start:end -->

## More examples

```go
package main

import (
	"context"
	"errors"
	"fmt"

	axioapi "github.com/axioapi/axioapi-go"
)

func main() {
	ctx := context.Background()
	client, err := axioapi.New("") // empty reads AXIOAPI_KEY
	if err != nil {
		panic(err)
	}

	// Keyword data API: volume, CPC and competition for up to 10 keywords per request
	var rows []struct {
		Keyword      string `json:"keyword"`
		SearchVolume int    `json:"search_volume"`
	}
	err = client.CallInto(ctx, "seo.keyword-metrics", axioapi.Params{"keywords": []string{"api gateway"}, "country": "us"}, &rows)

	// Backlink API: raw JSON, decode what you need
	data, err := client.Group("seo").Call(ctx, "backlinksSummary", axioapi.Params{"domain": "example.com"})
	fmt.Println(string(data), err)

	var apiErr *axioapi.APIError
	switch {
	case errors.Is(err, axioapi.ErrInsufficientCredits):
		fmt.Println("top up credits")
	case errors.As(err, &apiErr):
		fmt.Println(apiErr.Status, apiErr.Code, apiErr.RequestID, apiErr.RetryAfter)
	}
}
```

- `client.Group("seo").Call(ctx, "keywordMetrics", params)` or `client.Call(ctx, "seo.keyword-metrics", params)` for every endpoint (camelCase, snake_case or kebab-case). `client.Operations()` lists all of them with method, path and credit cost.
- `Call` returns the `data` field as `json.RawMessage`; `CallInto` decodes it; `Request` returns the status, headers and whole envelope; `CallBinary` returns file bytes.
- Errors are `*APIError` and match `errors.Is` with `ErrAuthentication`, `ErrInsufficientCredits`, `ErrNotFound`, `ErrValidation` and `ErrRateLimit`. Network failures are `*ConnectionError`.
- Options: `WithBaseURL`, `WithHTTPClient`, `WithMaxRetries(2)`, `WithUserAgent`. 429 is retried for all methods; 502/503/504 and network errors only for GET and DELETE. Pass a context to cancel.

Docs: https://axioapi.com/docs · Guides: https://axioapi.com/guides · License: MIT

<!-- seo:start -->
## What you can build with the Go SDK

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

**Is there a Go client for the AxioAPI backlink API and keyword data API?** Yes, this package. `seo.backlinks_summary` returns the backlink summary of a domain with the figures of every data source, and `seo.keyword_metrics` returns search volume, CPC and competition for up to 10 keywords per request.

**How do I test signup emails and OTP codes from Go?** Create a disposable inbox with the temp mail API, submit its address in your form and read the message. For SMS codes, call the verify endpoint, which waits for the OTP on a public number and returns it.

**How much does it cost?** You pay per request with credits, and each endpoint lists its price on its [API page](https://axioapi.com/apis) and in the OpenAPI spec (`x-credit-cost`). New accounts receive free credits after verification. See [pricing](https://axioapi.com/pricing).

**Where is the full reference?** [axioapi.com/docs](https://axioapi.com/docs), the [OpenAPI 3.1 spec](https://axioapi.com/api/v1/openapi.json) and [llms.txt](https://axioapi.com/llms.txt) for AI agents.

Vietnamese: [AxioAPI tiếng Việt](https://axioapi.com/vi), [API SEO và API backlink](https://axioapi.com/vi/apis/seo).
<!-- seo:end -->
