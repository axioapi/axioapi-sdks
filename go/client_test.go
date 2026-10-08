package axioapi

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"os"
	"testing"
	"time"
)

func mockURL() string {
	if u := os.Getenv("AXIOAPI_MOCK_URL"); u != "" {
		return u
	}
	return "http://127.0.0.1:8765"
}

func newClient(t *testing.T, key string, opts ...Option) *Client {
	t.Helper()
	resp, err := http.Get(mockURL() + "/__reset")
	if err != nil {
		t.Fatalf("mock server not running: %v", err)
	}
	resp.Body.Close()
	opts = append([]Option{WithBaseURL(mockURL()), WithSleep(func(context.Context, time.Duration) error { return nil })}, opts...)
	c, err := New(key, opts...)
	if err != nil {
		t.Fatal(err)
	}
	return c
}

func hits(t *testing.T) map[string]int {
	resp, err := http.Get(mockURL() + "/__hits")
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	b, _ := io.ReadAll(resp.Body)
	m := map[string]int{}
	_ = json.Unmarshal(b, &m)
	return m
}

type echo struct {
	Method      string              `json:"method"`
	Path        string              `json:"path"`
	Query       map[string][]string `json:"query"`
	Body        map[string]any      `json:"body"`
	UserAgent   string              `json:"user_agent"`
	ContentType string              `json:"content_type"`
}

func TestRequiresKey(t *testing.T) {
	t.Setenv("AXIOAPI_KEY", "")
	if _, err := New(""); err == nil {
		t.Fatal("expected error")
	}
}

func TestGroupCallSendsJSONBodyAndUnwrapsData(t *testing.T) {
	c := newClient(t, "test_key")
	data, err := c.Group("seo").Call(context.Background(), "keywordMetrics", Params{"keywords": []string{"api gateway", "proxy scraper"}, "country": "us"})
	if err != nil {
		t.Fatal(err)
	}
	var got echo
	if err := json.Unmarshal(data, &got); err != nil {
		t.Fatal(err)
	}
	if got.Method != "POST" || got.Path != "/api/v1/seo/keywords/metrics" || got.Body["country"] != "us" || len(got.Body["keywords"].([]any)) != 2 {
		t.Fatalf("unexpected echo: %+v", got)
	}
	if got.ContentType != "application/json" || got.UserAgent[:10] != "axioapi-go" {
		t.Fatalf("headers: %+v", got)
	}
}

func TestSnakeAndKebabNamesAndCallInto(t *testing.T) {
	c := newClient(t, "test_key")
	var got echo
	if err := c.CallInto(context.Background(), "seo.keyword-metrics", Params{"keywords": []string{"a"}}, &got); err != nil || got.Path != "/api/v1/seo/keywords/metrics" {
		t.Fatalf("kebab: %v %+v", err, got)
	}
	data, err := c.Group("seo").Call(context.Background(), "keyword_metrics", Params{"keywords": []string{"a"}})
	if err != nil || len(data) == 0 {
		t.Fatalf("snake: %v", err)
	}
}

func TestPathParamsEncodedAndQuerySeparated(t *testing.T) {
	c := newClient(t, "test_key")
	var got echo
	if err := c.CallInto(context.Background(), "seo.backlinks-summary", Params{"domain": "exa mple.com"}, &got); err != nil {
		t.Fatal(err)
	}
	if got.Path != "/api/v1/seo/domains/exa%20mple.com/backlinks" && got.Path != "/api/v1/seo/domains/exa mple.com/backlinks" {
		t.Fatalf("path: %s", got.Path)
	}
	if err := c.CallInto(context.Background(), "seo.keyword-suggestions", Params{"q": "laravel api", "country": "us", "lang": "en", "skipped": nil}, &got); err != nil {
		t.Fatal(err)
	}
	if got.Query["q"][0] != "laravel api" || len(got.Query) != 3 {
		t.Fatalf("query: %+v", got.Query)
	}
}

func TestMissingPathParamAndUnknownOperation(t *testing.T) {
	c := newClient(t, "test_key")
	if _, err := c.Call(context.Background(), "seo.backlinks-summary", nil); err == nil {
		t.Fatal("expected missing path param error")
	}
	if _, err := c.Call(context.Background(), "nope.nothing", nil); err == nil {
		t.Fatal("expected unknown operation error")
	}
	if _, err := c.Group("seo").Call(context.Background(), "doesNotExist", nil); err == nil {
		t.Fatal("expected unknown group operation error")
	}
}

func TestEveryRegistryOperationIsReachable(t *testing.T) {
	c := newClient(t, "test_key")
	if len(c.Operations()) < 100 {
		t.Fatalf("registry too small: %d", len(c.Operations()))
	}
	for key := range c.Operations() {
		group, rest := key[:indexDot(key)], key[indexDot(key)+1:]
		if got, ok := c.Resolve(group, rest); !ok || got != key {
			t.Fatalf("cannot resolve %s", key)
		}
	}
}

func indexDot(s string) int {
	for i := 0; i < len(s); i++ {
		if s[i] == '.' {
			return i
		}
	}
	return -1
}

func TestRawEnvelopeAndBinary(t *testing.T) {
	c := newClient(t, "test_key")
	res, err := c.Request(context.Background(), "GET", "/api/v1/account/limits", nil, nil)
	if err != nil || len(res.Envelope) == 0 {
		t.Fatalf("raw: %v", err)
	}
	var env struct {
		Status  string `json:"status"`
		Request struct {
			ID string `json:"id"`
		} `json:"request"`
	}
	_ = json.Unmarshal(res.Envelope, &env)
	if env.Status != "success" || env.Request.ID != "req_test_1" {
		t.Fatalf("envelope: %+v", env)
	}
	b, err := c.CallBinary(context.Background(), "ai-image.artifact", Params{"job": "abc"})
	if err != nil || b[0] != 0x89 {
		t.Fatalf("binary: %v", err)
	}
	if _, err := c.Call(context.Background(), "ai-image.artifact", Params{"job": "abc"}); err == nil {
		t.Fatal("Call on a binary operation should fail")
	}
}

func TestErrorsMapToSentinels(t *testing.T) {
	ctx := context.Background()
	_, err := newClient(t, "wrong").Group("account").Call(ctx, "limits", nil)
	var apiErr *APIError
	if !errors.Is(err, ErrAuthentication) || !errors.As(err, &apiErr) || apiErr.Status != 401 || apiErr.RequestID != "req_test_1" {
		t.Fatalf("auth: %v", err)
	}
	if _, err := newClient(t, "nocredit").Group("account").Call(ctx, "limits", nil); !errors.Is(err, ErrInsufficientCredits) {
		t.Fatalf("402: %v", err)
	}
	c := newClient(t, "test_key")
	if _, err := c.Request(ctx, "GET", "/api/v1/temp-mail/inboxes/missing", nil, nil); !errors.Is(err, ErrNotFound) {
		t.Fatalf("404: %v", err)
	}
	_, err = c.Group("seo").Call(ctx, "onPageAudit", nil)
	if !errors.Is(err, ErrValidation) || !errors.As(err, &apiErr) || apiErr.Fields["url"][0] != "The url field is required." {
		t.Fatalf("422: %v", err)
	}
	_, err = c.Request(ctx, "GET", "/api/v1/boom", nil, nil)
	if !errors.As(err, &apiErr) || apiErr.Status != 500 {
		t.Fatalf("500: %v", err)
	}
}

func TestRetries(t *testing.T) {
	ctx := context.Background()
	c := newClient(t, "test_key")
	res, err := c.Request(ctx, "GET", "/api/v1/flaky", nil, nil)
	if err != nil {
		t.Fatal(err)
	}
	var d struct{ Attempts int }
	_ = json.Unmarshal(res.Data, &d)
	if d.Attempts != 3 {
		t.Fatalf("flaky attempts %d", d.Attempts)
	}
	if res, err = c.Request(ctx, "GET", "/api/v1/ratelimited", nil, nil); err != nil {
		t.Fatal(err)
	}
	_ = json.Unmarshal(res.Data, &d)
	if d.Attempts != 2 {
		t.Fatalf("429 attempts %d", d.Attempts)
	}
	c1 := newClient(t, "test_key", WithMaxRetries(1))
	_, err = c1.Request(ctx, "POST", "/api/v1/always429", nil, map[string]any{})
	var apiErr *APIError
	if !errors.As(err, &apiErr) || apiErr.RetryAfter != 7 || hits(t)["always429"] != 2 {
		t.Fatalf("always429: %v %v", err, hits(t))
	}
	if _, err = c.Request(ctx, "POST", "/api/v1/post503", nil, map[string]any{}); err == nil || hits(t)["post503"] != 1 {
		t.Fatalf("post503 should not be retried: %v %v", err, hits(t))
	}
}

func TestConnectionError(t *testing.T) {
	c, _ := New("k", WithBaseURL("http://127.0.0.1:1"), WithMaxRetries(0), WithHTTPClient(&http.Client{Timeout: 2 * time.Second}))
	_, err := c.Group("account").Call(context.Background(), "limits", nil)
	var conn *ConnectionError
	if !errors.As(err, &conn) {
		t.Fatalf("expected ConnectionError, got %v", err)
	}
}
