package com.example.fileprocessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestJackson {
    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        
        // Scenario 1: Correctly parsed ObjectNode
        JsonNode objectNode = mapper.readTree("{\"field\": \"value\"}");
        System.out.println("ObjectNode isEmpty(): " + objectNode.isEmpty()); // Should be false
        
        // Scenario 2: String/TextNode containing JSON string
        JsonNode textNode = mapper.valueToTree("{\"field\": \"value\"}");
        System.out.println("TextNode isEmpty(): " + textNode.isEmpty()); // Will this be true?
        
        // Scenario 3: TextNode but just a normal string
        JsonNode stringNode = mapper.valueToTree("hello");
        System.out.println("StringNode isEmpty(): " + stringNode.isEmpty());
        System.out.println("StringNode isTextual(): " + stringNode.isTextual());
    }
}
