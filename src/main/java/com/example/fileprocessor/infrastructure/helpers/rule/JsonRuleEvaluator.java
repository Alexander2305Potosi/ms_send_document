package com.example.fileprocessor.infrastructure.helpers.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class JsonRuleEvaluator {

    private static final Logger log = Logger.getLogger(JsonRuleEvaluator.class.getName());
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, JsonNode> ruleCache = new ConcurrentHashMap<>();

    public JsonRuleEvaluator() {
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }

    public boolean evaluate(String ruleJson, Object dto) {
        JsonNode ruleNode;
        if (ruleJson == null || ruleJson.trim().isEmpty()) {
            ruleNode = mapper.createObjectNode();
        } else {
            ruleNode = ruleCache.computeIfAbsent(ruleJson, k -> {
                try {
                    return mapper.readTree(k);
                } catch (Exception e) {
                    log.log(Level.WARNING, "Failed to parse rule JSON, using default", e);
                    return mapper.createObjectNode();
                }
            });
        }

        JsonNode dtoNode = mapper.valueToTree(dto);
        if (ruleNode == null || ruleNode.isEmpty()) {
            log.log(Level.FINE, "Rule node is null or empty, evaluating to true (default fallback)");
            return true;
        }
        
        if (log.isLoggable(Level.FINE)) {
            log.log(Level.FINE, "Evaluating rule: {0} against DTO", ruleNode);
        }

        Iterator<Map.Entry<String, JsonNode>> ruleFields = ruleNode.fields();
        while (ruleFields.hasNext()) {
            Map.Entry<String, JsonNode> ruleField = ruleFields.next();
            String fieldName = ruleField.getKey();
            JsonNode conditions = ruleField.getValue();
            JsonNode actualValueNode = dtoNode.get(fieldName);
            String actualValue = (actualValueNode != null && !actualValueNode.isNull()) ? actualValueNode.asText() : null;
            
            if (log.isLoggable(Level.FINE)) {
                log.log(Level.FINE, "Checking field '{0}' with conditions {1}. Actual value: '{2}'", 
                        new Object[]{fieldName, conditions, actualValue});
            }

            if (!evaluateStringConditions(conditions, actualValue)) {
                log.log(Level.FINE, "Rule evaluation failed on field '{0}'", fieldName);
                return false;
            }
        }
        
        log.log(Level.FINE, "Rule evaluation succeeded for all fields");
        return true;
    }

    private boolean evaluateStringConditions(JsonNode ruleCriteria, String actualValue) {
        if (actualValue == null || actualValue.trim().isEmpty()) {
            log.log(Level.FINE, "Actual value is empty/null, failing condition");
            return false;
        }
        
        String value = sanitize(actualValue);
        
        if (ruleCriteria.has("$eq") && !value.equals(sanitize(ruleCriteria.get("$eq").asText()))) {
            return false;
        }
        
        if (ruleCriteria.has("$contains") && !value.contains(sanitize(ruleCriteria.get("$contains").asText()))) {
            return false;
        }
        
        if (ruleCriteria.has("$regex") && !value.matches(ruleCriteria.get("$regex").asText())) {
            return false;
        }
        
        if (ruleCriteria.has("$in")) {
            boolean matchIn = false;
            for (JsonNode allowedValue : ruleCriteria.get("$in")) {
                if (value.equals(sanitize(allowedValue.asText()))) {
                    matchIn = true;
                    break;
                }
            }
            if (!matchIn) return false;
        }
        
        if (ruleCriteria.has("$containsAny")) {
            boolean matchContainsAny = false;
            for (JsonNode keyword : ruleCriteria.get("$containsAny")) {
                if (value.contains(sanitize(keyword.asText()))) {
                    matchContainsAny = true;
                    break;
                }
            }
            if (!matchContainsAny) return false;
        }
        
        return true;
    }

    private String sanitize(String text) {
        if (text == null) return null;
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").toLowerCase();
    }
}
