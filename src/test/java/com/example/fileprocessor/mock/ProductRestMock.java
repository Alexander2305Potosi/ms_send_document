package com.example.fileprocessor.mock;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * Mock REST API Server for product document retrieval.
 * Simulates an external REST API that provides products with associated documents.
 *
 * Endpoints:
 *   GET /api/products                       - Returns list of all products with documents
 *   GET /api/products/{productId}/documents/{documentId} - Returns specific document
 *
 * Usage: java ProductRestMock [puerto]
 *   - puerto: numero de puerto (default: 3001)
 */
public class ProductRestMock {

    private static final int DEFAULT_PORT = 3001;
    private static final int PORT_RANGE_START = 3001;
    private static final int PORT_RANGE_END = 3999;

    private static final String PDF_CONTENT = createPdfContent();
    private static final String DOCX_CONTENT = createDocxContent();
    private static final String TXT_CONTENT = createTxtContent();

    public static void main(String[] args) throws IOException {
        int port = resolvePort(args);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/products", new ProductsListHandler());
        server.createContext("/api/products/prod-001/documents", new Product001DocumentsHandler());
        server.createContext("/api/products/prod-002/documents", new Product002DocumentsHandler());
        server.setExecutor(null);
        server.start();

        saveServerInfo(port);
        printBanner(port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nDeteniendo servidor de productos...");
            server.stop(0);
            cleanupServerInfo();
        }));

        synchronized (ProductRestMock.class) {
            try {
                ProductRestMock.class.wait();
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

    private static void saveServerInfo(int port) throws IOException {
        Path infoFile = getServerInfoFile();
        String content = "product-rest-port=" + port + "\n" +
                        "product-rest-endpoint=http://localhost:" + port + "\n" +
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
        return Paths.get(tempDir, "product-rest-mock.info");
    }

    private static void printBanner(int port) {
        String endpoint = "http://localhost:" + port;
        System.out.println();
        System.out.println("========================================");
        System.out.println("  Product REST Mock Server");
        System.out.println("========================================");
        System.out.println("  Puerto: " + port);
        System.out.println("  Endpoint base: " + endpoint);
        System.out.println("========================================");
        System.out.println();
        System.out.println("Endpoints disponibles:");
        System.out.println("  GET " + endpoint + "/api/products");
        System.out.println("  GET " + endpoint + "/api/products/{productId}/documents/{documentId}");
        System.out.println();
        System.out.println("Productos disponibles:");
        System.out.println("  1. prod-001 - Laptop Dell XPS 15 (2 documentos)");
        System.out.println("  2. prod-002 - TV Samsung 55\" (1 documento)");
        System.out.println();
        System.out.println("Variables de entorno:");
        System.out.println("  DOCUMENT_REST_ENDPOINT=" + endpoint);
        System.out.println();
        System.out.println("Presiona Ctrl+C para detener");
        System.out.println();
    }

    private static String createPdfContent() {
        String pdf = "%PDF-1.4\n" +
            "1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n" +
            "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n" +
            "3 0 obj<</Type/Page/MediaBox[0 0 612 792]/Parent 2 0 R>>endobj\n" +
            "xref\n" +
            "0 4\n" +
            "0000000000 65535 f\n" +
            "trailer<</Size 4/Root 1 0 R>>\n" +
            "startxref\n" +
            "190\n" +
            "%%EOF";
        return Base64.getEncoder().encodeToString(pdf.getBytes(StandardCharsets.UTF_8));
    }

    private static String createDocxContent() {
        byte[] docxBytes = new byte[] {0x50, 0x4B, 0x03, 0x04};
        return Base64.getEncoder().encodeToString(docxBytes);
    }

    private static String createTxtContent() {
        return Base64.getEncoder().encodeToString(
            "This is a test document content.\nLine 2.\nLine 3.".getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String createProductJson(String productId, String name, String documentsJson) {
        return String.format(
            "{\"productId\":\"%s\",\"name\":\"%s\",\"documents\":%s}",
            productId, name, documentsJson
        );
    }

    private static String createDocumentJson(String id, String filename, String content, String contentType, int size, boolean isZip, String origin) {
        return String.format(
            "{\"documentId\":\"%s\",\"filename\":\"%s\",\"content\":\"%s\",\"contentType\":\"%s\",\"size\":%d,\"isZip\":%b,\"origin\":\"%s\"}",
            id, filename, content, contentType, size, isZip, origin
        );
    }

    static class ProductsListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            System.out.println("[" + method + "] " + path);

            if (!"GET".equalsIgnoreCase(method)) {
                sendJsonResponse(exchange, 405, "{\"error\":\"METHOD_NOT_ALLOWED\",\"message\":\"Only GET is supported\"}");
                return;
            }

            String doc1 = createDocumentJson("doc-001", "manual.pdf", PDF_CONTENT, "application/pdf", PDF_CONTENT.length(), false, "folderA/incoming");
            String doc2 = createDocumentJson("doc-002", "specs.pdf", DOCX_CONTENT, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", DOCX_CONTENT.length(), false, "folderA/specs");
            String doc3 = createDocumentJson("doc-003", "manual.txt", TXT_CONTENT, "text/plain", TXT_CONTENT.length(), false, "folderB/incoming");

            String laptopDocs = "[" + doc1 + "," + doc2 + "]";
            String tvDocs = "[" + doc3 + "]";

            String prod1 = createProductJson("prod-001", "Laptop Dell XPS 15", laptopDocs);
            String prod2 = createProductJson("prod-002", "TV Samsung 55\"", tvDocs);

            String jsonResponse = "[" + prod1 + "," + prod2 + "]";
            sendJsonResponse(exchange, 200, jsonResponse);
            System.out.println("Response sent: 2 products with documents");
        }
    }

    static class Product001DocumentsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            System.out.println("[" + method + "] " + path);

            if (!"GET".equalsIgnoreCase(method)) {
                sendJsonResponse(exchange, 405, "{\"error\":\"METHOD_NOT_ALLOWED\",\"message\":\"Only GET is supported\"}");
                return;
            }

            String pathInfo = exchange.getHttpContext().getPath();
            String remainingPath = path.substring(pathInfo.length());

            String documentId;
            if (remainingPath.startsWith("/")) {
                documentId = remainingPath.substring(1);
            } else {
                documentId = remainingPath;
            }
            documentId = URLDecoder.decode(documentId, StandardCharsets.UTF_8);

            if (documentId.isEmpty()) {
                String doc1 = createDocumentJson("doc-001", "manual.pdf", PDF_CONTENT, "application/pdf", PDF_CONTENT.length(), false, "folderA/incoming");
                String doc2 = createDocumentJson("doc-002", "specs.pdf", DOCX_CONTENT, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", DOCX_CONTENT.length(), false, "folderA/specs");
                sendJsonResponse(exchange, 200, "[" + doc1 + "," + doc2 + "]");
                System.out.println("Response sent: all documents for prod-001");
                return;
            }

            System.out.println("Document requested for prod-001: " + documentId);

            String jsonResponse = switch (documentId) {
                case "doc-001" -> createDocumentJson("doc-001", "manual.pdf", PDF_CONTENT, "application/pdf", PDF_CONTENT.length(), false, "folderA/incoming");
                case "doc-002" -> createDocumentJson("doc-002", "specs.pdf", DOCX_CONTENT, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", DOCX_CONTENT.length(), false, "folderA/specs");
                default -> null;
            };

            if (jsonResponse == null) {
                sendJsonResponse(exchange, 404, "{\"error\":\"NOT_FOUND\",\"message\":\"Document not found: " + documentId + "\"}");
            } else {
                sendJsonResponse(exchange, 200, jsonResponse);
                System.out.println("Response sent: document " + documentId);
            }
        }
    }

    static class Product002DocumentsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            System.out.println("[" + method + "] " + path);

            if (!"GET".equalsIgnoreCase(method)) {
                sendJsonResponse(exchange, 405, "{\"error\":\"METHOD_NOT_ALLOWED\",\"message\":\"Only GET is supported\"}");
                return;
            }

            String pathInfo = exchange.getHttpContext().getPath();
            String remainingPath = path.substring(pathInfo.length());

            String documentId;
            if (remainingPath.startsWith("/")) {
                documentId = remainingPath.substring(1);
            } else {
                documentId = remainingPath;
            }
            documentId = URLDecoder.decode(documentId, StandardCharsets.UTF_8);

            if (documentId.isEmpty()) {
                String doc1 = createDocumentJson("doc-003", "manual.txt", TXT_CONTENT, "text/plain", TXT_CONTENT.length(), false, "folderB/incoming");
                sendJsonResponse(exchange, 200, "[" + doc1 + "]");
                System.out.println("Response sent: all documents for prod-002");
                return;
            }

            System.out.println("Document requested for prod-002: " + documentId);

            String jsonResponse = switch (documentId) {
                case "doc-003" -> createDocumentJson("doc-003", "manual.txt", TXT_CONTENT, "text/plain", TXT_CONTENT.length(), false, "folderB/incoming");
                default -> null;
            };

            if (jsonResponse == null) {
                sendJsonResponse(exchange, 404, "{\"error\":\"NOT_FOUND\",\"message\":\"Document not found: " + documentId + "\"}");
            } else {
                sendJsonResponse(exchange, 200, jsonResponse);
                System.out.println("Response sent: document " + documentId);
            }
        }
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
