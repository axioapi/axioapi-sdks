# axioapi-go

Go client for the [AxioAPI](https://axioapi.com) REST API: temp mail API, receive SMS and OTP API, email validation API, proxy API, SEO API (keyword data and backlink API) and social scraper APIs. One API key, pay per request. Standard library only. Go 1.21+.

```bash
go get github.com/axioapi/axioapi-go
export AXIOAPI_KEY=ak_...
```

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
