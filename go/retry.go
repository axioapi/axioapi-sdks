package axioapi

import (
	"context"
	"math"
	"net/http"
	"strconv"
	"time"
)

const (
	maxBackoffSeconds    = 8.0
	maxRetryAfterSeconds = 30.0
)

// retryPolicy retries 429 for every method; 5xx and network errors only for idempotent ones.
type retryPolicy struct {
	maxRetries int
	sleep      func(context.Context, time.Duration) error
}

func isIdempotent(method string) bool {
	return method == http.MethodGet || method == http.MethodHead || method == http.MethodDelete
}

func (p retryPolicy) shouldRetryStatus(status int, method string, attempt int) bool {
	if attempt >= p.maxRetries {
		return false
	}
	retryableServerError := status == http.StatusBadGateway || status == http.StatusServiceUnavailable || status == http.StatusGatewayTimeout
	return status == http.StatusTooManyRequests || (isIdempotent(method) && retryableServerError)
}

func (p retryPolicy) shouldRetryConnection(method string, attempt int) bool {
	return attempt < p.maxRetries && isIdempotent(method)
}

func (p retryPolicy) wait(ctx context.Context, attempt int, retryAfter string) error {
	return p.sleep(ctx, backoff(attempt, retryAfter))
}

func backoff(attempt int, retryAfter string) time.Duration {
	if seconds, err := strconv.ParseFloat(retryAfter, 64); err == nil {
		return time.Duration(math.Min(seconds, maxRetryAfterSeconds) * float64(time.Second))
	}
	return time.Duration(math.Min(0.5*math.Pow(2, float64(attempt)), maxBackoffSeconds) * float64(time.Second))
}

func sleepContext(ctx context.Context, d time.Duration) error {
	timer := time.NewTimer(d)
	defer timer.Stop()
	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-timer.C:
		return nil
	}
}
