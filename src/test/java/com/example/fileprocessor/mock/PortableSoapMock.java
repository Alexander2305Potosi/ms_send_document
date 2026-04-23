package com.example.fileprocessor.mock;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mock SOAP Server portable - funciona en cualquier puerto disponible.
 * Soporta multiples escenarios de respuesta rotando infinitamente:
 * 1. 200 Success
 * 2. 500 Server Error (reintentable)
 * 3. 503 Service Unavailable (reintentable)
 * 4. 504 Gateway Timeout (reintentable)
 * 5. 200 Slow Response (30s delay)
 * 6. 400 Bad Request
 *
 * Uso: java PortableSoapMock [puerto] [escenarios]
 *   - puerto: numero de puerto (default: 9000)
 *   - escenarios: numeros separados por coma, ej: "1" o "1,2,5"
 *
 * Si no se especifica puerto, busca uno disponible automaticamente.
 * Si no se especifican escenarios, rota entre todos.
 */
public class PortableSoapMock {

    private static final int DEFAULT_PORT = 9000;
    private static final int PORT_RANGE_START = 9000;
    private static final int PORT_RANGE_END = 9999;

    // Escenarios base
    private static final String RESPONSE_SUCCESS = """
        <?xml version="1.0" encoding="UTF-8"?>
        <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:file="http://example.com/fileservice">
           <soap:Header/>
           <soap:Body>
              <file:UploadFileResponse>
                 <file:status>SUCCESS</file:status>
                 <file:message>File uploaded and processed successfully</file:message>
                 <file:correlationId>corr-test-12345</file:correlationId>
                 <file:processedAt>2024-04-20T12:00:00Z</file:processedAt>
                 <file:externalReference>ext-ref-mock-001</file:externalReference>
              </file:UploadFileResponse>
           </soap:Body>
        </soap:Envelope>
        """;

    private static final String RESPONSE_500 = """
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

    private static final String RESPONSE_503 = """
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

    private static final String RESPONSE_504 = """
        <?xml version="1.0" encoding="UTF-8"?>
        <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
           <soap:Header/>
           <soap:Body>
              <soap:Fault>
                 <faultcode>soap:Server</faultcode>
                 <faultstring>Gateway Timeout - Upstream service did not respond</faultstring>
              </soap:Fault>
           </soap:Body>
        </soap:Envelope>
        """;

    private static final String RESPONSE_SLOW = """
        <?xml version="1.0" encoding="UTF-8"?>
        <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:file="http://example.com/fileservice">
           <soap:Header/>
           <soap:Body>
              <file:UploadFileResponse>
                 <file:status>SUCCESS</file:status>
                 <file:message>File uploaded after delay</file:message>
                 <file:correlationId>corr-slow-789</file:correlationId>
                 <file:processedAt>2024-04-20T12:00:30Z</file:processedAt>
                 <file:externalReference>ext-ref-slow</file:externalReference>
              </file:UploadFileResponse>
           </soap:Body>
        </soap:Envelope>
        """;

    private static final String RESPONSE_400 = """
        <?xml version="1.0" encoding="UTF-8"?>
        <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
           <soap:Header/>
           <soap:Body>
              <soap:Fault>
                 <faultcode>soap:Client</faultcode>
                 <faultstring>Bad Request - Invalid message format</faultstring>
              </soap:Fault>
           </soap:Body>
        </soap:Envelope>
        """;

    record Scenario(String responseXml, int statusCode, int delayMs, String label) {}

    public static void main(String[] args) throws IOException {
        int port = resolvePort(args);
        List<Scenario> scenarios = resolveScenarios(args);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/soap/fileservice", new SoapHandler(scenarios));
        server.setExecutor(null);
        server.start();

        saveServerInfo(port);
        printBanner(port, scenarios);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nDeteniendo servidor...");
            server.stop(0);
            cleanupServerInfo();
        }));

        synchronized (PortableSoapMock.class) {
            try {
                PortableSoapMock.class.wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static int resolvePort(String[] args) {
        if (args.length > 0) {
            try {
                int port = Integer.parseInt(args[0]);
                if (isPortAvailable(port)) {
                    return port;
                }
                System.err.println("Puerto " + port + " no disponible, buscando alternativa...");
            } catch (NumberFormatException e) {
                System.err.println("Argumento invalido: " + args[0]);
            }
        }

        if (isPortAvailable(DEFAULT_PORT)) {
            return DEFAULT_PORT;
        }

        for (int port = PORT_RANGE_START; port <= PORT_RANGE_END; port++) {
            if (isPortAvailable(port)) {
                System.out.println("Puerto " + DEFAULT_PORT + " ocupado, usando alternativo: " + port);
                return port;
            }
        }

        throw new RuntimeException("No se encontro ningun puerto disponible en el rango " + PORT_RANGE_START + "-" + PORT_RANGE_END);
    }

    private static boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static List<Scenario> resolveScenarios(String[] args) {
        List<Scenario> all = List.of(
            new Scenario(RESPONSE_SUCCESS, 200, 100, "200 SUCCESS"),
            new Scenario(RESPONSE_500, 500, 100, "500 SERVER ERROR"),
            new Scenario(RESPONSE_503, 503, 100, "503 UNAVAILABLE"),
            new Scenario(RESPONSE_504, 504, 100, "504 GATEWAY TIMEOUT"),
            new Scenario(RESPONSE_SLOW, 200, 30000, "200 SLOW RESPONSE"),
            new Scenario(RESPONSE_400, 400, 100, "400 BAD REQUEST")
        );

        if (args.length < 2) {
            return all;
        }

        String filter = args[1].trim();
        if (filter.isEmpty()) {
            return all;
        }

        List<Scenario> filtered = new ArrayList<>();
        for (String part : filter.split(",")) {
            try {
                int idx = Integer.parseInt(part.trim()) - 1;
                if (idx >= 0 && idx < all.size()) {
                    filtered.add(all.get(idx));
                } else {
                    System.err.println("Escenario invalido: " + (idx + 1) + ", ignorado.");
                }
            } catch (NumberFormatException e) {
                System.err.println("Argumento de escenario invalido: '" + part.trim() + "', ignorado.");
            }
        }

        if (filtered.isEmpty()) {
            System.err.println("No se seleccionaron escenarios validos. Usando todos.");
            return all;
        }

        return filtered;
    }

    private static void saveServerInfo(int port) throws IOException {
        Path infoFile = getServerInfoFile();
        String content = "port=" + port + "\n" +
                        "endpoint=http://localhost:" + port + "/soap/fileservice\n" +
                        "timestamp=" + System.currentTimeMillis() + "\n";
        Files.writeString(infoFile, content);
    }

    private static void cleanupServerInfo() {
        try {
            Files.deleteIfExists(getServerInfoFile());
        } catch (IOException e) {
            // Ignorar error al limpiar
        }
    }

    private static Path getServerInfoFile() {
        String tempDir = System.getProperty("java.io.tmpdir");
        return Paths.get(tempDir, "file-processor-mock.info");
    }

    private static void printBanner(int port, List<Scenario> scenarios) {
        String endpoint = "http://localhost:" + port + "/soap/fileservice";

        System.out.println();
        System.out.println("========================================");
        System.out.println("  SOAP Mock Server (Portable)");
        System.out.println("========================================");
        System.out.println("  Puerto: " + port);
        System.out.println("  Endpoint: " + endpoint);
        System.out.println("========================================");
        System.out.println();
        System.out.println("Escenarios activos (rotacion infinita):");
        for (int i = 0; i < scenarios.size(); i++) {
            Scenario s = scenarios.get(i);
            System.out.println("  " + (i + 1) + ". " + s.label() + "          - delay=" + s.delayMs() + "ms");
        }
        System.out.println();
        System.out.println("Variables de entorno:");
        System.out.println("  SOAP_ENDPOINT=" + endpoint);
        System.out.println();
        System.out.println("Presiona Ctrl+C para detener");
        System.out.println();
    }

    static class SoapHandler implements HttpHandler {

        private final AtomicInteger counter = new AtomicInteger(0);
        private final List<Scenario> scenarios;

        SoapHandler(List<Scenario> scenarios) {
            this.scenarios = scenarios;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            System.out.println("[" + method + "] " + path);

            String requestBody = readRequestBody(exchange.getRequestBody());
            System.out.println("Request received, length: " + requestBody.length());

            int idx = counter.getAndIncrement() % scenarios.size();
            Scenario s = scenarios.get(idx);

            if (s.delayMs() > 0) {
                try {
                    Thread.sleep(s.delayMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            byte[] responseBytes = s.responseXml().getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "text/xml; charset=UTF-8");
            if (s.statusCode() == 503) {
                exchange.getResponseHeaders().set("Retry-After", "30");
            }
            exchange.sendResponseHeaders(s.statusCode(), responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }

            System.out.println("Response sent: " + s.label() + " (escenario #" + (idx + 1) + ")");
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
