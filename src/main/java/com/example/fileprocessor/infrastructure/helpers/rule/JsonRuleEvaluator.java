package com.example.fileprocessor.infrastructure.helpers.rule;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class JsonRuleEvaluator {

    private static final Logger log = Logger.getLogger(JsonRuleEvaluator.class.getName());

    public boolean evaluate(JsonNode ruleNode, JsonNode dtoNode) {
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
        
        String value = actualValue.toLowerCase();
        
        if (ruleCriteria.has("$eq") && !value.equals(ruleCriteria.get("$eq").asText().toLowerCase())) {
            return false;
        }
        
        if (ruleCriteria.has("$contains") && !value.contains(ruleCriteria.get("$contains").asText().toLowerCase())) {
            return false;
        }
        
        if (ruleCriteria.has("$regex") && !value.matches(ruleCriteria.get("$regex").asText())) {
            return false;
        }
        
        if (ruleCriteria.has("$in")) {
            boolean matchIn = false;
            for (JsonNode allowedValue : ruleCriteria.get("$in")) {
                if (value.equals(allowedValue.asText().toLowerCase())) {
                    matchIn = true;
                    break;
                }
            }
            if (!matchIn) return false;
        }
        
        if (ruleCriteria.has("$containsAny")) {
            boolean matchContainsAny = false;
            for (JsonNode keyword : ruleCriteria.get("$containsAny")) {
                if (value.contains(keyword.asText().toLowerCase())) {
                    matchContainsAny = true;
                    break;
                }
            }
            if (!matchContainsAny) return false;
        }
        
        return true;
    }
}
