package com.example.fileprocessor.mock;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mock SOAP Server avanzado con múltiples respuestas en secuencia.
 *
 * Ejecutar: ./start-advanced-mock.sh
 */
public class AdvancedSoapMock {

    // Contador para rotar respuestas
    private static final AtomicInteger requestCount = new AtomicInteger(0);

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8081), 0);

        server.createContext("/soap/fileservice", new SoapHandler());

        server.setExecutor(null);
        server.start();

        System.out.println("========================================");
        System.out.println("Advanced SOAP Mock Server");
        System.out.println("Port: 8081");
        System.out.println("Endpoint: http://localhost:8081/soap/fileservice");
        System.out.println("");
        System.out.println("Respuestas en secuencia:");
        System.out.println("  1. 200 OK - Éxito");
        System.out.println("  2. 500 Internal Server Error");
        System.out.println("  3. 503 Service Unavailable");
        System.out.println("  4. 504 Gateway Timeout");
        System.out.println("  5. 200 OK - Delay 5 segundos");
        System.out.println("  6. 400 Bad Request");
        System.out.println("");
        System.out.println("Presiona Ctrl+C para detener");
        System.out.println("========================================");
    }

    static class SoapHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            int count = requestCount.incrementAndGet();

            System.out.println("[" + count + "] " + method + " " + exchange.getRequestURI().getPath());

            // Leer el request (solo para logging)
            String requestBody = readRequestBody(exchange.getRequestBody());
            System.out.println("Request body length: " + requestBody.length());

            // Seleccionar respuesta basada en el contador
            int responseType = ((count - 1) % 6) + 1; // Rotar entre 1-6

            switch (responseType) {
                case 1 -> sendSuccessResponse(exchange, count);
                case 2 -> sendError500Response(exchange);
                case 3 -> sendError503Response(exchange);
                case 4 -> sendError504Response(exchange);
                case 5 -> sendSlowResponse(exchange, count);
                case 6 -> sendError400Response(exchange);
                default -> sendSuccessResponse(exchange, count);
            }
        }

        private void sendSuccessResponse(HttpExchange exchange, int count) throws IOException {
            String responseXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:file="http://example.com/fileservice">
                   <soap:Header/>
                   <soap:Body>
                      <file:UploadFileResponse>
                         <file:status>SUCCESS</file:status>
                         <file:message>File processed successfully</file:message>
                         <file:correlationId>corr-test-%d</file:correlationId>
                         <file:processedAt>2024-04-20T12:00:00Z</file:processedAt>
                         <file:externalReference>ext-ref-mock-%d</file:externalReference>
                      </file:UploadFileResponse>
                   </soap:Body>
                </soap:Envelope>
                """.formatted(count, count);

            sendResponse(exchange, 200, responseXml);
            System.out.println("[RESPONSE] 200 OK - Success");
        }

        private void sendError500Response(HttpExchange exchange) throws IOException {
            String responseXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                   <soap:Header/>
                   <soap:Body>
                      <soap:Fault>
                         <faultcode>soap:Server</faultcode>
                         <faultstring>Internal Server Error - Temporary failure</faultstring>
                      </soap:Fault>
                   </soap:Body>
                </soap:Envelope>
                """;

            sendResponse(exchange, 500, responseXml);
            System.out.println("[RESPONSE] 500 Internal Server Error");
        }

        private void sendError503Response(HttpExchange exchange) throws IOException {
            String responseXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                   <soap:Header/>
                   <soap:Body>
                      <soap:Fault>
                         <faultcode>soap:Server</faultcode>
                         <faultstring>Service Temporarily Unavailable</faultstring>
                      </soap:Fault>
                   </soap:Body>
                </soap:Envelope>
                """;

            exchange.getResponseHeaders().set("Retry-After", "30");
            sendResponse(exchange, 503, responseXml);
            System.out.println("[RESPONSE] 503 Service Unavailable");
        }

        private void sendError504Response(HttpExchange exchange) throws IOException {
            String responseXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                   <soap:Header/>
                   <soap:Body>
                      <soap:Fault>
                         <faultcode>soap:Server</faultcode>
                         <faultstring>Gateway Timeout</faultstring>
                      </soap:Fault>
                   </soap:Body>
                </soap:Envelope>
                """;

            sendResponse(exchange, 504, responseXml);
            System.out.println("[RESPONSE] 504 Gateway Timeout");
        }

        private void sendSlowResponse(HttpExchange exchange, int count) throws IOException {
            System.out.println("[RESPONSE] 200 OK - Delay 5 segundos...");

            try {
                Thread.sleep(5000); // Delay de 5 segundos
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            String responseXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:file="http://example.com/fileservice">
                   <soap:Header/>
                   <soap:Body>
                      <file:UploadFileResponse>
                         <file:status>SUCCESS</file:status>
                         <file:message>File processed after delay</file:message>
                         <file:correlationId>corr-delayed-%d</file:correlationId>
                         <file:processedAt>2024-04-20T12:00:00Z</file:processedAt>
                         <file:externalReference>ext-ref-delayed</file:externalReference>
                      </file:UploadFileResponse>
                   </soap:Body>
                </soap:Envelope>
                """.formatted(count);

            sendResponse(exchange, 200, responseXml);
            System.out.println("[RESPONSE] 200 OK - Delay completado");
        }

        private void sendError400Response(HttpExchange exchange) throws IOException {
            String responseXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                   <soap:Header/>
                   <soap:Body>
                      <soap:Fault>
                         <faultcode>soap:Client</faultcode>
                         <faultstring>Bad Request - Invalid input</faultstring>
                      </soap:Fault>
                   </soap:Body>
                </soap:Envelope>
                """;

            sendResponse(exchange, 400, responseXml);
            System.out.println("[RESPONSE] 400 Bad Request");
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String responseXml) throws IOException {
            byte[] responseBytes = responseXml.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/xml; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
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
