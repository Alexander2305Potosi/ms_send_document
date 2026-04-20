package com.example.fileprocessor.mock;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import java.io.IOException;
import java.util.Scanner;

/**
 * Mock SOAP Server standalone para pruebas locales.
 * Ejecutar: ./gradlew testClasses && java -cp "build/classes/java/test:build/classes/java/main:$(./gradlew -q printTestClasspath)" com.example.fileprocessor.mock.SoapMockServer
 */
public class SoapMockServer {

    public static void main(String[] args) throws IOException {
        MockWebServer server = new MockWebServer();

        // Success response
        String successResponse = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:file="http://example.com/fileservice">
               <soap:Header/>
               <soap:Body>
                  <file:UploadFileResponse>
                     <file:status>SUCCESS</file:status>
                     <file:message>File uploaded and processed successfully</file:message>
                     <file:correlationId>corr-test-123</file:correlationId>
                     <file:processedAt>2024-01-15T10:30:00Z</file:processedAt>
                     <file:externalReference>ext-ref-abc</file:externalReference>
                  </file:UploadFileResponse>
               </soap:Body>
            </soap:Envelope>
            """;

        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(successResponse)
            .addHeader("Content-Type", "text/xml; charset=UTF-8"));

        server.start(8081);

        System.out.println("SOAP Mock Server running on http://localhost:8081/soap/fileservice");
        System.out.println("Press Enter to stop...");

        new Scanner(System.in).nextLine();
        server.shutdown();
    }
}
