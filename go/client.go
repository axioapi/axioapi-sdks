// Package axioapi is the Go client for the AxioAPI REST API (https://axioapi.com): temp mail, SMS and OTP,
// email validation, proxies, SEO and backlink data and social scrapers.
//
//	c, _ := axioapi.New("ak_...")
//	data, err := c.Group("seo").Call(ctx, "keywordMetrics", axioapi.Params{"keywords": []string{"api gateway"}, "country": "us"})
package axioapi

import (
	"bytes"
	"context"
	_ "embed"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math"
	"net/http"
	"net/url"
	"os"
	"regexp"
	"strconv"
	"strings"
	"time"
)

// Version is the SDK version.
const Version = "1.0.0"

const defaultBaseURL = "https://axioapi.com"

//go:embed operations.json
var operationsJSON []byte

// Params are the parameters of an operation: path, query and body values in one map.
type Params map[string]any

// Operation describes one API operation from the registry.
type Operation struct {
	Method     string   `json:"method"`
	Path       string   `json:"path"`
	Summary    string   `json:"summary"`
	Cost       *float64 `json:"cost"`
	PathParams []string `json:"path_params"`
	Query      []string `json:"query"`
	Body       []string `json:"body"`
	Binary     bool     `json:"binary"`
}

// Client talks to the AxioAPI. It is safe for concurrent use.
type Client struct {
	apiKey     string
	baseURL    string
	userAgent  string
	maxRetries int
	http       *http.Client
	sleep      func(context.Context, time.Duration) error
	operations map[string]Operation
	index      map[string]string
}

// Option configures a Client.
type Option func(*Client)

// WithBaseURL overrides https://axioapi.com.
func WithBaseURL(u string) Option { return func(c *Client) { c.baseURL = strings.TrimRight(u, "/") } }

// WithHTTPClient replaces the default http.Client (set your own Timeout or Transport).
func WithHTTPClient(h *http.Client) Option { return func(c *Client) { c.http = h } }

// WithMaxRetries sets retries for 429 and, on GET/DELETE, 502/503/504 and network errors. Default 2.
func WithMaxRetries(n int) Option { return func(c *Client) { c.maxRetries = max(0, n) } }

// WithUserAgent overrides the User-Agent header.
func WithUserAgent(ua string) Option { return func(c *Client) { c.userAgent = ua } }

// WithSleep replaces the backoff sleep (used by tests).
func WithSleep(f func(context.Context, time.Duration) error) Option {
	return func(c *Client) { c.sleep = f }
}

var nonAlnum = regexp.MustCompile(`[^a-z0-9]`)

func normalize(s string) string { return nonAlnum.ReplaceAllString(strings.ToLower(s), "") }

// New creates a client. An empty apiKey falls back to the AXIOAPI_KEY environment variable.
func New(apiKey string, opts ...Option) (*Client, error) {
	if apiKey == "" {
		apiKey = os.Getenv("AXIOAPI_KEY")
	}
	if apiKey == "" {
		return nil, errors.New("axioapi: pass an API key or set the AXIOAPI_KEY environment variable")
	}
	base := os.Getenv("AXIOAPI_BASE_URL")
	if base == "" {
		base = defaultBaseURL
	}
	c := &Client{
		apiKey: apiKey, baseURL: strings.TrimRight(base, "/"), userAgent: "axioapi-go/" + Version, maxRetries: 2,
		http:  &http.Client{Timeout: 30 * time.Second},
		sleep: sleepContext,
	}
	for _, opt := range opts {
		opt(c)
	}
	var doc struct {
		Operations map[string]Operation `json:"operations"`
	}
	if err := json.Unmarshal(operationsJSON, &doc); err != nil {
		return nil, fmt.Errorf("axioapi: bad operations registry: %w", err)
	}
	c.operations = doc.Operations
	c.index = make(map[string]string, len(doc.Operations))
	for key := range doc.Operations {
		group, rest, _ := strings.Cut(key, ".")
		c.index[normalize(group)+"."+normalize(rest)] = key
	}
	return c, nil
}

func sleepContext(ctx context.Context, d time.Duration) error {
	t := time.NewTimer(d)
	defer t.Stop()
	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-t.C:
		return nil
	}
}

// Operations returns the registry of every operation.
func (c *Client) Operations() map[string]Operation { return c.operations }

// Resolve returns the capability key for a group and operation name (any of camelCase, snake_case, kebab-case).
func (c *Client) Resolve(group, name string) (string, bool) {
	key, ok := c.index[normalize(group)+"."+normalize(name)]
	return key, ok
}

// Group scopes calls to one API group, for example c.Group("seo").Call(ctx, "keywordMetrics", params).
func (c *Client) Group(name string) *Group { return &Group{client: c, name: name} }

// Group is a set of operations that share a prefix.
type Group struct {
	client *Client
	name   string
}

func (g *Group) key(op string) (string, error) {
	key, ok := g.client.Resolve(g.name, op)
	if !ok {
		return "", fmt.Errorf("axioapi: no operation %q in group %q", op, g.name)
	}
	return key, nil
}

// Call runs an operation and returns its data.
func (g *Group) Call(ctx context.Context, op string, params Params) (json.RawMessage, error) {
	key, err := g.key(op)
	if err != nil {
		return nil, err
	}
	return g.client.Call(ctx, key, params)
}

// CallBinary runs an operation that returns a file.
func (g *Group) CallBinary(ctx context.Context, op string, params Params) ([]byte, error) {
	key, err := g.key(op)
	if err != nil {
		return nil, err
	}
	return g.client.CallBinary(ctx, key, params)
}

// Response is a raw API response.
type Response struct {
	Status int
	Header http.Header
	// Data is the "data" field of the envelope (nil for files).
	Data json.RawMessage
	// Envelope is the whole JSON body (nil for files).
	Envelope json.RawMessage
	// Bytes is set instead of Data when the response is not JSON (images, audio).
	Bytes []byte
}

func (c *Client) prepare(operation string, params Params) (Operation, string, url.Values, map[string]any, error) {
	key := operation
	op, ok := c.operations[key]
	if !ok {
		group, rest, found := strings.Cut(operation, ".")
		if found {
			key, ok = c.Resolve(group, rest)
			op = c.operations[key]
		}
		if !ok {
			return op, "", nil, nil, fmt.Errorf("axioapi: unknown operation %q", operation)
		}
	}
	rest := make(Params, len(params))
	for k, v := range params {
		rest[k] = v
	}
	path := op.Path
	for _, name := range op.PathParams {
		v, present := rest[name]
		if !present || v == nil {
			return op, "", nil, nil, fmt.Errorf("axioapi: missing path parameter %q for %s", name, key)
		}
		path = strings.ReplaceAll(path, "{"+name+"}", url.PathEscape(fmt.Sprint(v)))
		delete(rest, name)
	}
	hasBody := op.Method != "GET" && op.Method != "DELETE" && op.Method != "HEAD"
	query := url.Values{}
	body := map[string]any{}
	for name, value := range rest {
		if value == nil {
			continue
		}
		if hasBody && (contains(op.Body, name) || !contains(op.Query, name)) {
			body[name] = value
		} else {
			addQuery(query, name, value)
		}
	}
	if !hasBody || len(body) == 0 {
		body = nil
	}
	return op, path, query, body, nil
}

func contains(list []string, s string) bool {
	for _, v := range list {
		if v == s {
			return true
		}
	}
	return false
}

func addQuery(q url.Values, key string, value any) {
	switch v := value.(type) {
	case []string:
		for _, item := range v {
			q.Add(key+"[]", item)
		}
	case []int:
		for _, item := range v {
			q.Add(key+"[]", strconv.Itoa(item))
		}
	case []any:
		for _, item := range v {
			q.Add(key+"[]", fmt.Sprint(item))
		}
	case bool:
		q.Set(key, strconv.FormatBool(v))
	default:
		q.Set(key, fmt.Sprint(v))
	}
}

// Call runs an operation by capability key (for example "seo.keyword-metrics") and returns its data.
func (c *Client) Call(ctx context.Context, operation string, params Params) (json.RawMessage, error) {
	op, path, query, body, err := c.prepare(operation, params)
	if err != nil {
		return nil, err
	}
	if op.Binary {
		return nil, fmt.Errorf("axioapi: %s returns a file; use CallBinary", operation)
	}
	res, err := c.Request(ctx, op.Method, path, query, body)
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
	op, path, query, body, err := c.prepare(operation, params)
	if err != nil {
		return nil, err
	}
	res, err := c.Request(ctx, op.Method, path, query, body)
	if err != nil {
		return nil, err
	}
	if res.Bytes == nil {
		return nil, fmt.Errorf("axioapi: %s did not return a file", operation)
	}
	return res.Bytes, nil
}

// Request sends a request with retries and returns the parsed response. body may be nil.
func (c *Client) Request(ctx context.Context, method, path string, query url.Values, body any) (*Response, error) {
	method = strings.ToUpper(method)
	if !strings.HasPrefix(path, "/") {
		path = "/" + path
	}
	target := c.baseURL + path
	if len(query) > 0 {
		target += "?" + query.Encode()
	}
	var payload []byte
	if body != nil {
		var err error
		if payload, err = json.Marshal(body); err != nil {
			return nil, fmt.Errorf("axioapi: encode body: %w", err)
		}
	}
	idempotent := method == "GET" || method == "HEAD" || method == "DELETE"

	for attempt := 0; ; attempt++ {
		req, err := http.NewRequestWithContext(ctx, method, target, bytes.NewReader(payload))
		if err != nil {
			return nil, err
		}
		req.Header.Set("Authorization", "Bearer "+c.apiKey)
		req.Header.Set("Accept", "application/json")
		req.Header.Set("User-Agent", c.userAgent)
		if payload != nil {
			req.Header.Set("Content-Type", "application/json")
		}
		resp, err := c.http.Do(req)
		if err != nil {
			if ctx.Err() != nil {
				return nil, ctx.Err()
			}
			if idempotent && attempt < c.maxRetries {
				if serr := c.sleep(ctx, backoff("", attempt)); serr != nil {
					return nil, serr
				}
				continue
			}
			return nil, &ConnectionError{Message: fmt.Sprintf("could not reach %s: %v", c.baseURL, err)}
		}
		content, readErr := io.ReadAll(resp.Body)
		resp.Body.Close()
		if readErr != nil {
			return nil, &ConnectionError{Message: readErr.Error()}
		}
		if resp.StatusCode >= 200 && resp.StatusCode < 300 {
			return parseResponse(resp, content)
		}
		retryable := resp.StatusCode == 429 || (idempotent && (resp.StatusCode == 502 || resp.StatusCode == 503 || resp.StatusCode == 504))
		if retryable && attempt < c.maxRetries {
			if serr := c.sleep(ctx, backoff(resp.Header.Get("Retry-After"), attempt)); serr != nil {
				return nil, serr
			}
			continue
		}
		return nil, buildError(resp, content)
	}
}

func parseResponse(resp *http.Response, content []byte) (*Response, error) {
	res := &Response{Status: resp.StatusCode, Header: resp.Header}
	if !strings.Contains(resp.Header.Get("Content-Type"), "json") {
		res.Bytes = content
		return res, nil
	}
	if len(bytes.TrimSpace(content)) == 0 {
		return res, nil
	}
	res.Envelope = content
	var env struct {
		Data json.RawMessage `json:"data"`
	}
	if err := json.Unmarshal(content, &env); err != nil {
		return nil, fmt.Errorf("axioapi: invalid JSON response: %w", err)
	}
	res.Data = env.Data
	return res, nil
}

func backoff(retryAfter string, attempt int) time.Duration {
	if retryAfter != "" {
		if secs, err := strconv.ParseFloat(retryAfter, 64); err == nil {
			return time.Duration(math.Min(secs, 30) * float64(time.Second))
		}
	}
	return time.Duration(math.Min(0.5*math.Pow(2, float64(attempt)), 8) * float64(time.Second))
}
