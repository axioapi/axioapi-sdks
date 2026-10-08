package com.axioapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Every API operation, loaded from operations.json. */
final class Registry {
    private static final String RESOURCE = "/operations.json";

    private final Map<String, Operation> operations = new LinkedHashMap<>();
    private final Map<String, String> index = new HashMap<>();
    private final Set<String> groups = new HashSet<>();

    static Registry load() {
        try (InputStream in = Registry.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " resource is missing.");
            }
            Registry registry = new Registry();
            Iterator<Map.Entry<String, JsonNode>> entries = new ObjectMapper().readTree(in).get("operations").fields();
            while (entries.hasNext()) {
                Map.Entry<String, JsonNode> entry = entries.next();
                registry.add(parse(entry.getKey(), entry.getValue()));
            }
            return registry;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + RESOURCE, e);
        }
    }

    Map<String, Operation> all() {
        return Collections.unmodifiableMap(operations);
    }

    boolean hasGroup(String group) {
        return groups.contains(Naming.normalize(group));
    }

    /** Capability key for a group and operation name in any spelling, or null. */
    String resolve(String group, String name) {
        return index.get(indexKey(group, name));
    }

    /** Looks an operation up by exact key or by any spelling of group.name; null when unknown. */
    Operation find(String operation) {
        Operation exact = operations.get(operation);
        if (exact != null) {
            return exact;
        }
        int dot = operation.indexOf('.');
        if (dot <= 0) {
            return null;
        }
        String key = resolve(operation.substring(0, dot), operation.substring(dot + 1));
        return key == null ? null : operations.get(key);
    }

    private void add(Operation operation) {
        int dot = operation.key.indexOf('.');
        String group = operation.key.substring(0, dot);
        operations.put(operation.key, operation);
        groups.add(Naming.normalize(group));
        index.put(indexKey(group, operation.key.substring(dot + 1)), operation.key);
    }

    private static String indexKey(String group, String name) {
        return Naming.normalize(group) + "." + Naming.normalize(name);
    }

    private static Operation parse(String key, JsonNode node) {
        JsonNode cost = node.get("cost");
        return new Operation(key, node.get("method").asText(), node.get("path").asText(), node.path("summary").asText(""),
            cost != null && cost.isNumber() ? cost.asDouble() : null,
            strings(node.get("path_params")), strings(node.get("query")), strings(node.get("body")), node.path("binary").asBoolean(false));
    }

    private static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        if (array != null) {
            array.forEach(item -> out.add(item.asText()));
        }
        return Collections.unmodifiableList(out);
    }
}
