package com.example.fileprocessor.mock;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mock S3 Server for testing S3 document upload.
 * Simulates AWS S3 PUT object behavior.
 *
 * Endpoints:
 *   PUT /{bucket}/{key} - Upload an object (Base64 content in request body)
 *
 * Headers expected:
 *   Content-Type: content type of the object
 *   x-amz-meta-traceId: trace ID for logging
 *   x-amz-meta-original-filename: original filename
 *   x-amz-meta-timestamp: timestamp
 *
 * Response:
 *   ETag: "\"etag-value\""
 *
 * Usage: java S3Mock [puerto] [bucket]
 *   - puerto: numero de puerto (default: 4566)
 *   - bucket: nombre del bucket (default: documents-bucket)
 */
public class S3Mock {

    private static final int DEFAULT_PORT = 4566;
    private static final int PORT_RANGE_START = 4566;
    private static final int PORT_RANGE_END = 4999;
    private static final String DEFAULT_BUCKET = "documents-bucket";

    private static final String RESPONSE_SUCCESS = """
        <?xml version="1.0" encoding="UTF-8"?>
        <PutObjectResponse xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
            <ETag>"%s"</ETag>
            <LastModified>2024-04-20T12:00:00.000Z</LastModified>
            <ContentLength>%d</ContentLength>
        </PutObjectResponse>
        """;

    public static void main(String[] args) throws IOException {
        int port = resolvePort(args);
        String bucket = resolveBucket(args);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/" + bucket, new S3Handler());
        server.setExecutor(null);
        server.start();

        saveServerInfo(port, bucket);
        printBanner(port, bucket);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nDeteniendo S3 Mock...");
            server.stop(0);
            cleanupServerInfo();
        }));

        synchronized (S3Mock.class) {
            try {
                S3Mock.class.wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static String resolveBucket(String[] args) {
        if (args.length > 1) {
            String bucket = args[1].trim();
            if (!bucket.isEmpty()) {
                return bucket;
            }
        }
        return DEFAULT_BUCKET;
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

    private static void saveServerInfo(int port, String bucket) throws IOException {
        Path infoFile = getServerInfoFile();
        String content = "s3-mock-port=" + port + "\n" +
                        "s3-mock-bucket=" + bucket + "\n" +
                        "s3-mock-endpoint=http://localhost:" + port + "\n" +
                        "s3-mock-url=http://localhost:" + port + "/" + bucket + "\n" +
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
        return Paths.get(tempDir, "s3-mock.info");
    }

    private static void printBanner(int port, String bucket) {
        String endpoint = "http://localhost:" + port + "/" + bucket;
        System.out.println();
        System.out.println("========================================");
        System.out.println("  S3 Mock Server");
        System.out.println("========================================");
        System.out.println("  Puerto: " + port);
        System.out.println("  Bucket: " + bucket);
        System.out.println("  Endpoint: " + endpoint);
        System.out.println("========================================");
        System.out.println();
        System.out.println("Simula AWS S3 PUT Object con:");
        System.out.println("  PUT " + endpoint + "/{key}");
        System.out.println();
        System.out.println("Headers esperados:");
        System.out.println("  Content-Type: application/pdf, text/plain, etc.");
        System.out.println("  x-amz-meta-traceId: trace-uuid");
        System.out.println("  x-amz-meta-original-filename: file.pdf");
        System.out.println("  x-amz-meta-timestamp: 2024-04-20T12:00:00Z");
        System.out.println();
        System.out.println("Variables de entorno:");
        System.out.println("  AWS_ENDPOINT=" + "http://localhost:" + port);
        System.out.println("  AWS_BUCKET=" + bucket);
        System.out.println();
        System.out.println("Presiona Ctrl+C para detener");
        System.out.println();
    }

    static class S3Handler implements HttpHandler {
        private final AtomicInteger requestCount = new AtomicInteger(0);

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            int count = requestCount.incrementAndGet();
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            System.out.println("[" + count + "] " + method + " " + path);

            // Log headers
            exchange.getRequestHeaders().forEach((name, values) -> {
                if (name.toLowerCase().startsWith("x-amz-meta") ||
                    name.equalsIgnoreCase("Content-Type")) {
                    System.out.println("  " + name + ": " + values.get(0));
                }
            });

            if (!"PUT".equalsIgnoreCase(method)) {
                System.out.println("  Method not allowed: " + method);
                sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
                return;
            }

            try {
                // Read request body (should be raw bytes)
                byte[] requestBody;
                try (var is = exchange.getRequestBody()) {
                    requestBody = is.readAllBytes();
                }

                String contentType = "application/octet-stream";
                var ctHeader = exchange.getRequestHeaders().getFirst("Content-Type");
                if (ctHeader != null) {
                    contentType = ctHeader;
                }

                String traceId = "unknown";
                var traceHeader = exchange.getRequestHeaders().getFirst("x-amz-meta-traceId");
                if (traceHeader != null) {
                    traceId = traceHeader;
                }

                String originalFilename = "unknown";
                var filenameHeader = exchange.getRequestHeaders().getFirst("x-amz-meta-original-filename");
                if (filenameHeader != null) {
                    originalFilename = filenameHeader;
                }

                String timestamp = "unknown";
                var tsHeader = exchange.getRequestHeaders().getFirst("x-amz-meta-timestamp");
                if (tsHeader != null) {
                    timestamp = tsHeader;
                }

                // Extract key from path (remove /bucket/ prefix)
                String key = path;
                int idx = key.indexOf('/');
                if (idx > 0) {
                    key = key.substring(idx + 1);
                }

                // Generate ETag
                String etag = String.format("%s-%d", UUID.randomUUID().toString().substring(0, 8), count);

                System.out.println("  Uploaded: " + originalFilename + " (" + requestBody.length + " bytes)");
                System.out.println("  Key: " + key);
                System.out.println("  Content-Type: " + contentType);
                System.out.println("  TraceId: " + traceId);
                System.out.println("  ETag: " + etag);

                // Set response headers
                exchange.getResponseHeaders().set("ETag", "\"" + etag + "\"");
                exchange.getResponseHeaders().set("Content-Type", "application/xml");

                // Send success response
                String responseXml = String.format(RESPONSE_SUCCESS, etag, requestBody.length);
                byte[] responseBytes = responseXml.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, responseBytes.length);

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }

                System.out.println("  Response: 200 OK");

            } catch (Exception e) {
                System.out.println("  Error: " + e.getMessage());
                sendResponse(exchange, 500, "text/plain", "Internal Server Error: " + e.getMessage());
            }
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String contentType, String message) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    static class UUID {
        public static String randomUUID() {
            return java.util.UUID.randomUUID().toString();
        }
    }
}
