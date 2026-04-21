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

/**
 * Mock SOAP Server portable - funciona en cualquier puerto disponible
 * Uso: java PortableSoapMock [puerto]
 * Si no se especifica puerto, busca uno disponible automáticamente
 */
public class PortableSoapMock {

    private static final int DEFAULT_PORT = 9000;
    private static final int PORT_RANGE_START = 9000;
    private static final int PORT_RANGE_END = 9999;

    public static void main(String[] args) throws IOException {
        int port = resolvePort(args);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/soap/fileservice", new SoapHandler());
        server.setExecutor(null);
        server.start();

        // Guardar información del servidor en un archivo para que otras herramientas la lean
        saveServerInfo(port);

        printBanner(port);

        // Mantener el servidor vivo
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nDeteniendo servidor...");
            server.stop(0);
            cleanupServerInfo();
        }));

        // Bloquear el hilo principal
        synchronized (PortableSoapMock.class) {
            try {
                PortableSoapMock.class.wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static int resolvePort(String[] args) {
        // 1. Si se pasa argumento, usar ese puerto
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

        // 2. Intentar puerto por defecto
        if (isPortAvailable(DEFAULT_PORT)) {
            return DEFAULT_PORT;
        }

        // 3. Buscar puerto libre en rango alternativo
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
        // Guardar en directorio temporal del sistema
        String tempDir = System.getProperty("java.io.tmpdir");
        return Paths.get(tempDir, "file-processor-mock.info");
    }

    private static void printBanner(int port) {
        String endpoint = "http://localhost:" + port + "/soap/fileservice";

        System.out.println();
        System.out.println("========================================");
        System.out.println("  SOAP Mock Server (Portable)");
        System.out.println("========================================");
        System.out.println("  Puerto: " + port);
        System.out.println("  Endpoint: " + endpoint);
        System.out.println("========================================");
        System.out.println();
        System.out.println("Variables de entorno configuradas:");
        System.out.println("  SOAP_ENDPOINT=" + endpoint);
        System.out.println();
        System.out.println("Presiona Ctrl+C para detener");
        System.out.println();
    }

    static class SoapHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            System.out.println("[" + method + "] " + path);

            // Leer el request
            String requestBody = readRequestBody(exchange.getRequestBody());
            System.out.println("Request received, length: " + requestBody.length());

            // Generar respuesta SOAP exitosa
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
