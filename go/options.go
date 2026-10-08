package axioapi

import (
	"context"
	"net/http"
	"strings"
	"time"
)

// Option configures a Client.
type Option func(*Client)

// WithBaseURL overrides https://axioapi.com.
func WithBaseURL(u string) Option { return func(c *Client) { c.baseURL = strings.TrimRight(u, "/") } }

// WithHTTPClient replaces the default http.Client (set your own Timeout or Transport).
func WithHTTPClient(h *http.Client) Option { return func(c *Client) { c.http = h } }

// WithMaxRetries sets retries for 429 and, on GET/DELETE, 502/503/504 and network errors. Default 2.
func WithMaxRetries(n int) Option { return func(c *Client) { c.retry.maxRetries = max(0, n) } }

// WithUserAgent overrides the User-Agent header.
func WithUserAgent(ua string) Option { return func(c *Client) { c.userAgent = ua } }

// WithSleep replaces the backoff sleep (used by tests).
func WithSleep(f func(context.Context, time.Duration) error) Option {
	return func(c *Client) { c.retry.sleep = f }
}
