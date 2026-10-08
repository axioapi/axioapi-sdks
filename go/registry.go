package axioapi

import (
	_ "embed" // operations.json is embedded
	"encoding/json"
	"fmt"
	"strings"
)

//go:embed operations.json
var operationsJSON []byte

// Operation describes one API operation from the registry.
type Operation struct {
	Key        string   `json:"-"`
	Method     string   `json:"method"`
	Path       string   `json:"path"`
	Summary    string   `json:"summary"`
	Cost       *float64 `json:"cost"`
	PathParams []string `json:"path_params"`
	Query      []string `json:"query"`
	Body       []string `json:"body"`
	Binary     bool     `json:"binary"`
}

func (o Operation) hasBody() bool {
	return o.Method != "GET" && o.Method != "DELETE" && o.Method != "HEAD"
}

// registry holds every operation and resolves them by any spelling of group and name.
type registry struct {
	operations map[string]Operation
	index      map[string]string
}

func loadRegistry() (*registry, error) {
	var doc struct {
		Operations map[string]Operation `json:"operations"`
	}
	if err := json.Unmarshal(operationsJSON, &doc); err != nil {
		return nil, fmt.Errorf("axioapi: bad operations registry: %w", err)
	}
	r := &registry{operations: make(map[string]Operation, len(doc.Operations)), index: make(map[string]string, len(doc.Operations))}
	for key, op := range doc.Operations {
		op.Key = key
		r.operations[key] = op
		group, name, _ := strings.Cut(key, ".")
		r.index[indexKey(group, name)] = key
	}
	return r, nil
}

func (r *registry) resolve(group, name string) (string, bool) {
	key, ok := r.index[indexKey(group, name)]
	return key, ok
}

// find looks an operation up by exact key or by any spelling of group.name.
func (r *registry) find(operation string) (Operation, bool) {
	if op, ok := r.operations[operation]; ok {
		return op, true
	}
	group, name, found := strings.Cut(operation, ".")
	if !found {
		return Operation{}, false
	}
	key, ok := r.resolve(group, name)
	if !ok {
		return Operation{}, false
	}
	return r.operations[key], true
}
