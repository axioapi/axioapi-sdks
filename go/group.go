package axioapi

import (
	"context"
	"encoding/json"
	"fmt"
)

// Group scopes calls to one API group, for example c.Group("seo").Call(ctx, "keywordMetrics", params).
func (c *Client) Group(name string) *Group { return &Group{client: c, name: name} }

// Group is a set of operations that share a prefix.
type Group struct {
	client *Client
	name   string
}

func (g *Group) key(operation string) (string, error) {
	key, ok := g.client.Resolve(g.name, operation)
	if !ok {
		return "", fmt.Errorf("axioapi: no operation %q in group %q", operation, g.name)
	}
	return key, nil
}

// Call runs an operation and returns its data.
func (g *Group) Call(ctx context.Context, operation string, params Params) (json.RawMessage, error) {
	key, err := g.key(operation)
	if err != nil {
		return nil, err
	}
	return g.client.Call(ctx, key, params)
}

// CallBinary runs an operation that returns a file.
func (g *Group) CallBinary(ctx context.Context, operation string, params Params) ([]byte, error) {
	key, err := g.key(operation)
	if err != nil {
		return nil, err
	}
	return g.client.CallBinary(ctx, key, params)
}
