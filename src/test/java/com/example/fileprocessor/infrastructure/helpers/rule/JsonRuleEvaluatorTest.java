package com.example.fileprocessor.infrastructure.helpers.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonRuleEvaluatorTest {

    private JsonRuleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new JsonRuleEvaluator(new ObjectMapper());
    }

    @Test
    void evaluate_withNullOrEmptyRule_returnsTrue() {
        Map<String, String> dto = Map.of("name", "test");
        assertTrue(evaluator.evaluate(null, dto));
        assertTrue(evaluator.evaluate("{}", dto));
        assertTrue(evaluator.evaluate("", dto));
    }

    @Test
    void evaluate_withEqOperator_matchesExactly() {
        String rule = "{\"name\": {\"$eq\": \"TEST\"}}";
        
        Map<String, String> matchingDto = Map.of("name", "test");
        assertTrue(evaluator.evaluate(rule, matchingDto));

        Map<String, String> nonMatchingDto = Map.of("name", "other");
        assertFalse(evaluator.evaluate(rule, nonMatchingDto));
    }

    @Test
    void evaluate_withContainsOperator_matchesSubstring() {
        String rule = "{\"folder\": {\"$contains\": \"docs\"}}";
        
        Map<String, String> matchingDto = Map.of("folder", "/shared/docs/public");
        assertTrue(evaluator.evaluate(rule, matchingDto));

        Map<String, String> nonMatchingDto = Map.of("folder", "/shared/private");
        assertFalse(evaluator.evaluate(rule, nonMatchingDto));
    }

    @Test
    void evaluate_withRegexOperator_matchesPattern() {
        String rule = "{\"filename\": {\"$regex\": \"^prod_[0-9]{4}\\\\.pdf$\"}}";
        
        Map<String, String> matchingDto = Map.of("filename", "prod_2023.pdf");
        assertTrue(evaluator.evaluate(rule, matchingDto));

        Map<String, String> nonMatchingDto = Map.of("filename", "prod_abc.pdf");
        assertFalse(evaluator.evaluate(rule, nonMatchingDto));
    }

    @Test
    void evaluate_withInOperator_matchesAnyInList() {
        String rule = "{\"country\": {\"$in\": [\"co\", \"pe\", \"cl\"]}}";
        
        Map<String, String> matchingDto = Map.of("country", "PE");
        assertTrue(evaluator.evaluate(rule, matchingDto));

        Map<String, String> nonMatchingDto = Map.of("country", "AR");
        assertFalse(evaluator.evaluate(rule, nonMatchingDto));
    }

    @Test
    void evaluate_withContainsAnyOperator_matchesAnySubstring() {
        String rule = "{\"folder\": {\"$containsAny\": [\"garantia\", \"oficina\"]}}";
        
        Map<String, String> matchingDto1 = Map.of("folder", "/docs/oficina/ventas");
        assertTrue(evaluator.evaluate(rule, matchingDto1));

        Map<String, String> matchingDto2 = Map.of("folder", "garantias2023");
        assertTrue(evaluator.evaluate(rule, matchingDto2));

        Map<String, String> nonMatchingDto = Map.of("folder", "/docs/general");
        assertFalse(evaluator.evaluate(rule, nonMatchingDto));
    }

    @Test
    void evaluate_withMultipleConditions_actsAsAnd() {
        String rule = "{\"folder\": {\"$contains\": \"docs\"}, \"country\": {\"$eq\": \"co\"}}";
        
        Map<String, String> matchingDto = Map.of("folder", "/docs/", "country", "CO");
        assertTrue(evaluator.evaluate(rule, matchingDto));

        Map<String, String> nonMatchingDto1 = Map.of("folder", "/images/", "country", "CO");
        assertFalse(evaluator.evaluate(rule, nonMatchingDto1));

        Map<String, String> nonMatchingDto2 = Map.of("folder", "/docs/", "country", "PE");
        assertFalse(evaluator.evaluate(rule, nonMatchingDto2));
    }

    @Test
    void evaluate_withAccents_ignoresAccentsAndCase() {
        String rule = "{\"city\": {\"$eq\": \"bogotá\"}, \"department\": {\"$containsAny\": [\"antioquía\", \"boyacá\"]}}";
        
        // DTO without accents, uppercase
        Map<String, String> matchingDto1 = Map.of("city", "BOGOTA", "department", "Antioquia");
        assertTrue(evaluator.evaluate(rule, matchingDto1));

        // DTO with accents, mixed case
        Map<String, String> matchingDto2 = Map.of("city", "BógOtä", "department", "bOyÁcA");
        assertTrue(evaluator.evaluate(rule, matchingDto2));
        
        // DTO not matching
        Map<String, String> nonMatchingDto = Map.of("city", "bogota", "department", "cundinamarca");
        assertFalse(evaluator.evaluate(rule, nonMatchingDto));
    }

    @Test
    void evaluate_whenFieldIsMissingInDto_returnsFalse() {
        String rule = "{\"name\": {\"$eq\": \"test\"}}";
        Map<String, String> dto = Map.of(); // Empty DTO
        assertFalse(evaluator.evaluate(rule, dto));
    }
}
