package com.example.fileprocessor.infrastructure.helpers.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonRuleEvaluatorTest {

    private JsonRuleEvaluator evaluator;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        evaluator = new JsonRuleEvaluator();
        mapper = new ObjectMapper();
    }

    @Test
    void evaluate_withNullOrEmptyRule_returnsTrue() {
        JsonNode dtoNode = mapper.createObjectNode().put("name", "test");
        assertTrue(evaluator.evaluate(null, dtoNode));
        assertTrue(evaluator.evaluate(mapper.createObjectNode(), dtoNode));
    }

    @Test
    void evaluate_withEqOperator_matchesExactly() throws Exception {
        JsonNode rule = mapper.readTree("{\"name\": {\"$eq\": \"TEST\"}}");
        
        JsonNode matchingDto = mapper.createObjectNode().put("name", "test");
        assertTrue(evaluator.evaluate(rule, matchingDto));

        JsonNode nonMatchingDto = mapper.createObjectNode().put("name", "other");
        assertFalse(evaluator.evaluate(rule, nonMatchingDto));
    }

    @Test
    void evaluate_withContainsOperator_matchesSubstring() throws Exception {
        JsonNode rule = mapper.readTree("{\"folder\": {\"$contains\": \"docs\"}}");
        
        JsonNode matchingDto = mapper.createObjectNode().put("folder", "/shared/docs/public");
        assertTrue(evaluator.evaluate(rule, matchingDto));

        JsonNode nonMatchingDto = mapper.createObjectNode().put("folder", "/shared/private");
        assertFalse(evaluator.evaluate(rule, nonMatchingDto));
    }

    @Test
    void evaluate_withRegexOperator_matchesPattern() throws Exception {
        JsonNode rule = mapper.readTree("{\"filename\": {\"$regex\": \"^prod_[0-9]{4}\\\\.pdf$\"}}");
        
        JsonNode matchingDto = mapper.createObjectNode().put("filename", "prod_2023.pdf");
        assertTrue(evaluator.evaluate(rule, matchingDto));

        JsonNode nonMatchingDto = mapper.createObjectNode().put("filename", "prod_abc.pdf");
        assertFalse(evaluator.evaluate(rule, nonMatchingDto));
    }

    @Test
    void evaluate_withInOperator_matchesAnyInList() throws Exception {
        JsonNode rule = mapper.readTree("{\"country\": {\"$in\": [\"co\", \"pe\", \"cl\"]}}");
        
        JsonNode matchingDto = mapper.createObjectNode().put("country", "PE");
        assertTrue(evaluator.evaluate(rule, matchingDto));

        JsonNode nonMatchingDto = mapper.createObjectNode().put("country", "AR");
        assertFalse(evaluator.evaluate(rule, nonMatchingDto));
    }

    @Test
    void evaluate_withContainsAnyOperator_matchesAnySubstring() throws Exception {
        JsonNode rule = mapper.readTree("{\"folder\": {\"$containsAny\": [\"garantia\", \"oficina\"]}}");
        
        JsonNode matchingDto1 = mapper.createObjectNode().put("folder", "/docs/oficina/ventas");
        assertTrue(evaluator.evaluate(rule, matchingDto1));

        JsonNode matchingDto2 = mapper.createObjectNode().put("folder", "garantias2023");
        assertTrue(evaluator.evaluate(rule, matchingDto2));

        JsonNode nonMatchingDto = mapper.createObjectNode().put("folder", "/docs/general");
        assertFalse(evaluator.evaluate(rule, nonMatchingDto));
    }

    @Test
    void evaluate_withMultipleConditions_actsAsAnd() throws Exception {
        JsonNode rule = mapper.readTree("{\"folder\": {\"$contains\": \"docs\"}, \"country\": {\"$eq\": \"co\"}}");
        
        JsonNode matchingDto = mapper.createObjectNode().put("folder", "/docs/").put("country", "CO");
        assertTrue(evaluator.evaluate(rule, matchingDto));

        JsonNode nonMatchingDto1 = mapper.createObjectNode().put("folder", "/images/").put("country", "CO");
        assertFalse(evaluator.evaluate(rule, nonMatchingDto1));

        JsonNode nonMatchingDto2 = mapper.createObjectNode().put("folder", "/docs/").put("country", "PE");
        assertFalse(evaluator.evaluate(rule, nonMatchingDto2));
    }

    @Test
    void evaluate_whenFieldIsMissingInDto_returnsFalse() throws Exception {
        JsonNode rule = mapper.readTree("{\"name\": {\"$eq\": \"test\"}}");
        JsonNode dtoNode = mapper.createObjectNode(); // Empty DTO
        assertFalse(evaluator.evaluate(rule, dtoNode));
    }
}
