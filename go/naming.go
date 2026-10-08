package axioapi

import (
	"regexp"
	"strings"
)

var nonAlphanumeric = regexp.MustCompile(`[^a-z0-9]`)

// normalize folds snake_case, camelCase and kebab-case to one comparable form.
func normalize(name string) string {
	return nonAlphanumeric.ReplaceAllString(strings.ToLower(name), "")
}

func indexKey(group, name string) string {
	return normalize(group) + "." + normalize(name)
}
