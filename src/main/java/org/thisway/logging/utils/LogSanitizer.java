package org.thisway.logging.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class LogSanitizer {

    private static final Set<String> SENSITIVE_KEYS = Set.of("password", "newPassword", "token");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static String sanitize(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            sanitizeNode(root);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return raw;
        }
    }

    private static void sanitizeNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode objNode = (ObjectNode) node;
            Iterator<Entry<String, JsonNode>> fields = objNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey();
                JsonNode value = entry.getValue();

                if (SENSITIVE_KEYS.contains(key)) {
                    objNode.put(key, "[FILTERED]");
                } else {
                    sanitizeNode(value);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                sanitizeNode(item);
            }
        }
    }
}
