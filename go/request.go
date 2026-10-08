package axioapi

import (
	"fmt"
	"net/url"
	"slices"
	"strconv"
	"strings"
)

// Params are the parameters of an operation: path, query and body values in one map.
type Params map[string]any

// preparedRequest is an operation with its parameters placed in path, query and body.
type preparedRequest struct {
	method string
	path   string
	query  url.Values
	body   map[string]any
}

// buildRequest splits flat params into path, query and body according to the operation.
func buildRequest(op Operation, params Params) (preparedRequest, error) {
	remaining := make(Params, len(params))
	for name, value := range params {
		remaining[name] = value
	}
	path, err := fillPath(op, remaining)
	if err != nil {
		return preparedRequest{}, err
	}
	query := url.Values{}
	body := map[string]any{}
	for name, value := range remaining {
		if value == nil {
			continue
		}
		if belongsInBody(op, name) {
			body[name] = value
		} else {
			addQuery(query, name, value)
		}
	}
	if !op.hasBody() || len(body) == 0 {
		body = nil
	}
	return preparedRequest{method: op.Method, path: path, query: query, body: body}, nil
}

func fillPath(op Operation, params Params) (string, error) {
	path := op.Path
	for _, name := range op.PathParams {
		value, present := params[name]
		if !present || value == nil {
			return "", fmt.Errorf("axioapi: missing path parameter %q for %s", name, op.Key)
		}
		path = strings.ReplaceAll(path, "{"+name+"}", url.PathEscape(fmt.Sprint(value)))
		delete(params, name)
	}
	return path, nil
}

func belongsInBody(op Operation, name string) bool {
	return op.hasBody() && (slices.Contains(op.Body, name) || !slices.Contains(op.Query, name))
}

func addQuery(query url.Values, key string, value any) {
	switch v := value.(type) {
	case []string:
		for _, item := range v {
			query.Add(key+"[]", item)
		}
	case []int:
		for _, item := range v {
			query.Add(key+"[]", strconv.Itoa(item))
		}
	case []any:
		for _, item := range v {
			query.Add(key+"[]", fmt.Sprint(item))
		}
	case bool:
		query.Set(key, strconv.FormatBool(v))
	default:
		query.Set(key, fmt.Sprint(v))
	}
}
