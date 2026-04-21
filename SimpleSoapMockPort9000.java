package com.example.fileprocessor.mock;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Mock SOAP Server en puerto 9000 (alternativa sin admin)
 * Compilar: javac SimpleSoapMockPort9000.java
 * Ejecutar: java SimpleSoapMockPort9000
 */
public class SimpleSoapMockPort9000 {

    public static void main(String[] args) throws IOException {
        int PORT = 9000; // Puerto alternativo

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/soap/fileservice", new SoapHandler());
        server.setExecutor(null);
        server.start();

        System.out.println("========================================");
        System.out.println("SOAP Mock Server running on port " + PORT);
        System.out.println("Endpoint: http://localhost:" + PORT + "/soap/fileservice");
        System.out.println("");
        System.out.println("Presiona Ctrl+C para detener");
        System.out.println("========================================");
    }

    static class SoapHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            System.out.println("[" + method + "] " + path);

            String requestBody = readRequestBody(exchange.getRequestBody());
            System.out.println("Request received, length: " + requestBody.length());

            String responseXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:file="http://example.com/fileservice">
                   <soap:Header/>
                   <soap:Body>
                      <file:UploadFileResponse>
                         <file:status>SUCCESS</file:status>
                         <file:message>File processed successfully</file:message>
                         <file:correlationId>corr-test-12345</file:correlationId>
                         <file:processedAt>2024-04-20T12:00:00Z</file:processedAt>
                         <file:externalReference>ext-ref-mock-001</file:externalReference>
                      </file:UploadFileResponse>
                   </soap:Body>
                </soap:Envelope>
                """;

            byte[] responseBytes = responseXml.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/xml; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);

            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();

            System.out.println("Response sent: 200 OK");
        }

        private String readRequestBody(InputStream is) throws IOException {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                return sb.toString();
            }
        }
    }
}
