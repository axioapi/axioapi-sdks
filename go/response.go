package axioapi

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
)

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

func parseSuccess(resp *http.Response, content []byte) (*Response, error) {
	res := &Response{Status: resp.StatusCode, Header: resp.Header}
	if !strings.Contains(resp.Header.Get("Content-Type"), "json") {
		res.Bytes = content
		return res, nil
	}
	if len(bytes.TrimSpace(content)) == 0 {
		return res, nil
	}
	var envelope struct {
		Data json.RawMessage `json:"data"`
	}
	if err := json.Unmarshal(content, &envelope); err != nil {
		return nil, fmt.Errorf("axioapi: invalid JSON response: %w", err)
	}
	res.Envelope = content
	res.Data = envelope.Data
	return res, nil
}
