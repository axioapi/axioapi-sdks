// Package axioapi is the Go client for the AxioAPI REST API (https://axioapi.com): temp mail, SMS and OTP,
// email validation, proxies, SEO and backlink data and social scrapers.
//
//	c, _ := axioapi.New("ak_...")
//	data, err := c.Group("seo").Call(ctx, "keywordMetrics", axioapi.Params{"keywords": []string{"api gateway"}, "country": "us"})
package axioapi

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"
)

// Version is the SDK version.
const Version = "1.0.0"

const (
	defaultBaseURL = "https://axioapi.com"
	defaultTimeout = 30 * time.Second
	defaultRetries = 2
)

// Client talks to the AxioAPI. It is safe for concurrent use.
type Client struct {
	apiKey    string
	baseURL   string
	userAgent string
	http      *http.Client
	retry     retryPolicy
	registry  *registry
}

// New creates a client. An empty apiKey falls back to the AXIOAPI_KEY environment variable.
func New(apiKey string, opts ...Option) (*Client, error) {
	if apiKey == "" {
		apiKey = os.Getenv("AXIOAPI_KEY")
	}
	if apiKey == "" {
		return nil, errors.New("axioapi: pass an API key or set the AXIOAPI_KEY environment variable")
	}
	reg, err := loadRegistry()
	if err != nil {
		return nil, err
	}
	c := &Client{
		apiKey:    apiKey,
		baseURL:   strings.TrimRight(envOr("AXIOAPI_BASE_URL", defaultBaseURL), "/"),
		userAgent: "axioapi-go/" + Version,
		http:      &http.Client{Timeout: defaultTimeout},
		retry:     retryPolicy{maxRetries: defaultRetries, sleep: sleepContext},
		registry:  reg,
	}
	for _, opt := range opts {
		opt(c)
	}
	return c, nil
}

func envOr(name, fallback string) string {
	if value := os.Getenv(name); value != "" {
		return value
	}
	return fallback
}

// Operations returns the registry of every operation.
func (c *Client) Operations() map[string]Operation { return c.registry.operations }

// Resolve returns the capability key for a group and operation name (any of camelCase, snake_case, kebab-case).
func (c *Client) Resolve(group, name string) (string, bool) { return c.registry.resolve(group, name) }

// Call runs an operation by capability key (for example "seo.keyword-metrics") and returns its data.
func (c *Client) Call(ctx context.Context, operation string, params Params) (json.RawMessage, error) {
	op, prepared, err := c.prepare(operation, params)
	if err != nil {
		return nil, err
	}
	if op.Binary {
		return nil, fmt.Errorf("axioapi: %s returns a file; use CallBinary", operation)
	}
	res, err := c.Request(ctx, prepared.method, prepared.path, prepared.query, prepared.body)
	if err != nil {
		return nil, err
	}
	return res.Data, nil
}

// CallInto runs an operation and decodes its data into out.
func (c *Client) CallInto(ctx context.Context, operation string, params Params, out any) error {
	data, err := c.Call(ctx, operation, params)
	if err != nil {
		return err
	}
	return json.Unmarshal(data, out)
}

// CallBinary runs an operation that returns a file (image or audio).
func (c *Client) CallBinary(ctx context.Context, operation string, params Params) ([]byte, error) {
	_, prepared, err := c.prepare(operation, params)
	if err != nil {
		return nil, err
	}
	res, err := c.Request(ctx, prepared.method, prepared.path, prepared.query, prepared.body)
	if err != nil {
		return nil, err
	}
	if res.Bytes == nil {
		return nil, fmt.Errorf("axioapi: %s did not return a file", operation)
	}
	return res.Bytes, nil
}

func (c *Client) prepare(operation string, params Params) (Operation, preparedRequest, error) {
	op, ok := c.registry.find(operation)
	if !ok {
		return Operation{}, preparedRequest{}, fmt.Errorf("axioapi: unknown operation %q", operation)
	}
	prepared, err := buildRequest(op, params)
	return op, prepared, err
}

// Request sends a request with retries and returns the parsed response. body may be nil.
func (c *Client) Request(ctx context.Context, method, path string, query url.Values, body any) (*Response, error) {
	method = strings.ToUpper(method)
	target := c.url(path, query)
	payload, err := encodeBody(body)
	if err != nil {
		return nil, err
	}

	for attempt := 0; ; attempt++ {
		resp, content, err := c.roundTrip(ctx, method, target, payload)
		if err != nil {
			if ctx.Err() != nil {
				return nil, ctx.Err()
			}
			if !c.retry.shouldRetryConnection(method, attempt) {
				return nil, &ConnectionError{Message: fmt.Sprintf("could not reach %s: %v", c.baseURL, err)}
			}
			if err := c.retry.wait(ctx, attempt, ""); err != nil {
				return nil, err
			}
			continue
		}
		if resp.StatusCode >= 200 && resp.StatusCode < 300 {
			return parseSuccess(resp, content)
		}
		if !c.retry.shouldRetryStatus(resp.StatusCode, method, attempt) {
			return nil, buildError(resp, content)
		}
		if err := c.retry.wait(ctx, attempt, resp.Header.Get("Retry-After")); err != nil {
			return nil, err
		}
	}
}

func (c *Client) roundTrip(ctx context.Context, method, target string, payload []byte) (*http.Response, []byte, error) {
	req, err := http.NewRequestWithContext(ctx, method, target, bytes.NewReader(payload))
	if err != nil {
		return nil, nil, err
	}
	req.Header.Set("Authorization", "Bearer "+c.apiKey)
	req.Header.Set("Accept", "application/json")
	req.Header.Set("User-Agent", c.userAgent)
	if payload != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	resp, err := c.http.Do(req)
	if err != nil {
		return nil, nil, err
	}
	defer resp.Body.Close()
	content, err := io.ReadAll(resp.Body)
	return resp, content, err
}

func (c *Client) url(path string, query url.Values) string {
	if !strings.HasPrefix(path, "/") {
		path = "/" + path
	}
	if len(query) == 0 {
		return c.baseURL + path
	}
	return c.baseURL + path + "?" + query.Encode()
}

func encodeBody(body any) ([]byte, error) {
	if body == nil {
		return nil, nil
	}
	payload, err := json.Marshal(body)
	if err != nil {
		return nil, fmt.Errorf("axioapi: encode body: %w", err)
	}
	return payload, nil
}
