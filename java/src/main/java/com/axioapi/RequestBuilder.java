package com.axioapi;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Splits flat params into path, query and body according to the operation. */
final class RequestBuilder {
    private RequestBuilder() {
    }

    static PreparedRequest build(Operation operation, Map<String, ?> params) {
        Map<String, Object> remaining = new LinkedHashMap<>(params == null ? Map.of() : params);
        String path = fillPath(operation, remaining);
        Map<String, Object> query = new LinkedHashMap<>();
        Map<String, Object> body = new LinkedHashMap<>();
        for (Map.Entry<String, Object> param : remaining.entrySet()) {
            if (param.getValue() == null) {
                continue;
            }
            (belongsInBody(operation, param.getKey()) ? body : query).put(param.getKey(), param.getValue());
        }
        return new PreparedRequest(operation.method, path, query, operation.hasBody() && !body.isEmpty() ? body : null);
    }

    static String encodeQuery(Map<String, ?> query) {
        List<String> pairs = new ArrayList<>();
        for (Map.Entry<String, ?> entry : query.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Iterable) {
                ((Iterable<?>) value).forEach(item -> pairs.add(pair(entry.getKey() + "[]", item)));
            } else if (value instanceof Object[]) {
                for (Object item : (Object[]) value) {
                    pairs.add(pair(entry.getKey() + "[]", item));
                }
            } else if (value != null) {
                pairs.add(pair(entry.getKey(), value));
            }
        }
        return String.join("&", pairs);
    }

    private static String fillPath(Operation operation, Map<String, Object> params) {
        String path = operation.path;
        for (String name : operation.pathParams) {
            Object value = params.remove(name);
            if (value == null) {
                throw new IllegalArgumentException("Missing path parameter '" + name + "' for " + operation.key);
            }
            path = path.replace("{" + name + "}", encode(String.valueOf(value)).replace("+", "%20"));
        }
        return path;
    }

    private static boolean belongsInBody(Operation operation, String name) {
        return operation.hasBody() && (operation.body.contains(name) || !operation.query.contains(name));
    }

    private static String pair(String key, Object value) {
        return encode(key) + "=" + encode(String.valueOf(value));
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }
}
