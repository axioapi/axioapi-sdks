package axioapi

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strconv"
)

// Sentinel errors for errors.Is.
var (
	ErrAuthentication      = errors.New("axioapi: authentication failed")
	ErrInsufficientCredits = errors.New("axioapi: insufficient credits")
	ErrNotFound            = errors.New("axioapi: not found")
	ErrValidation          = errors.New("axioapi: validation failed")
	ErrRateLimit           = errors.New("axioapi: rate limited")
)

var sentinelByStatus = map[int]error{
	http.StatusUnauthorized:        ErrAuthentication,
	http.StatusPaymentRequired:     ErrInsufficientCredits,
	http.StatusNotFound:            ErrNotFound,
	http.StatusUnprocessableEntity: ErrValidation,
	http.StatusTooManyRequests:     ErrRateLimit,
}

// APIError is returned for every non-2xx response. Use errors.Is with the Err* sentinels or errors.As to read fields.
type APIError struct {
	Status    int
	Code      string
	Message   string
	RequestID string
	// Fields holds per-field messages for 422 responses.
	Fields map[string][]string
	// RetryAfter is in seconds for 429 responses, 0 when the server sent none.
	RetryAfter float64
	Body       json.RawMessage
}

func (e *APIError) Error() string {
	s := fmt.Sprintf("axioapi: %s (status %d", e.Message, e.Status)
	if e.Code != "" {
		s += ", code " + e.Code
	}
	if e.RequestID != "" {
		s += ", request_id " + e.RequestID
	}
	return s + ")"
}

// Is lets errors.Is(err, axioapi.ErrRateLimit) and friends match by HTTP status.
func (e *APIError) Is(target error) bool {
	return sentinelByStatus[e.Status] == target
}

// ConnectionError means the request never produced an HTTP response (DNS, TLS, timeout).
type ConnectionError struct{ Message string }

func (e *ConnectionError) Error() string { return "axioapi: " + e.Message }

type errorBody struct {
	Message string `json:"message"`
	Error   *struct {
		Code      string              `json:"code"`
		Message   string              `json:"message"`
		RequestID string              `json:"request_id"`
		Fields    map[string][]string `json:"fields"`
	} `json:"error"`
}

func buildError(resp *http.Response, content []byte) *APIError {
	e := &APIError{Status: resp.StatusCode, Message: "HTTP " + strconv.Itoa(resp.StatusCode), RequestID: resp.Header.Get("X-Request-Id")}
	var body errorBody
	if json.Unmarshal(content, &body) == nil {
		e.Body = content
		applyBody(e, body)
	}
	if resp.StatusCode == http.StatusTooManyRequests {
		e.RetryAfter, _ = strconv.ParseFloat(resp.Header.Get("Retry-After"), 64)
	}
	return e
}

func applyBody(e *APIError, body errorBody) {
	if body.Message != "" {
		e.Message = body.Message
	}
	if body.Error == nil {
		return
	}
	if body.Error.Message != "" {
		e.Message = body.Error.Message
	}
	e.Code = body.Error.Code
	e.Fields = body.Error.Fields
	if body.Error.RequestID != "" {
		e.RequestID = body.Error.RequestID
	}
}
