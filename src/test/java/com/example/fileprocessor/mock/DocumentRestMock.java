package com.example.fileprocessor.mock;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;

/**
 * Mock REST API Server for document retrieval.
 * Simulates an external REST API that provides documents to be processed.
 *
 * Supports 4 document scenarios:
 * 1. PDF Document (application/pdf)
 * 2. DOCX Document (application/vnd.openxmlformats-officedocument.wordprocessingml.document)
 * 3. TXT Document (text/plain)
 * 4. ZIP Archive containing multiple documents
 *
 * Usage: java DocumentRestMock [puerto]
 *   - puerto: numero de puerto (default: 8081)
 *
 * If no port is specified, searches for an available port starting from 8081.
 *
 * Endpoints:
 *   GET /api/documents       - Returns list of all documents
 *   GET /api/document/{id}   - Returns specific document by ID
 */
public class DocumentRestMock {

    private static final int DEFAULT_PORT = 8081;
    private static final int PORT_RANGE_START = 8081;
    private static final int PORT_RANGE_END = 9999;

    private static final String PDF_CONTENT = createPdfContent();
    private static final String DOCX_CONTENT = createDocxContent();
    private static final String TXT_CONTENT = createTxtContent();

    public static void main(String[] args) throws IOException {
        int port = resolvePort(args);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/documents", new DocumentsListHandler());
        server.createContext("/api/document", new DocumentByIdHandler());
        server.setExecutor(null);
        server.start();

        saveServerInfo(port);
        printBanner(port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nDeteniendo servidor de documentos...");
            server.stop(0);
            cleanupServerInfo();
        }));

        synchronized (DocumentRestMock.class) {
            try {
                DocumentRestMock.class.wait();
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
        String content = "document-rest-port=" + port + "\n" +
                        "document-rest-endpoint=http://localhost:" + port + "\n" +
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
        return Paths.get(tempDir, "document-rest-mock.info");
    }

    private static void printBanner(int port) {
        String endpoint = "http://localhost:" + port;
        System.out.println();
        System.out.println("========================================");
        System.out.println("  Document REST Mock Server");
        System.out.println("========================================");
        System.out.println("  Puerto: " + port);
        System.out.println("  Endpoint base: " + endpoint);
        System.out.println("========================================");
        System.out.println();
        System.out.println("Endpoints disponibles:");
        System.out.println("  GET " + endpoint + "/api/documents");
        System.out.println("  GET " + endpoint + "/api/document/{id}");
        System.out.println();
        System.out.println("Documentos disponibles:");
        System.out.println("  1. test-document.pdf    (application/pdf)");
        System.out.println("  2. test-document.docx   (application/vnd.openxmlformats-officedocument.wordprocessingml.document)");
        System.out.println("  3. test-document.txt     (text/plain)");
        System.out.println("  4. documents.zip         (application/zip) - Contains 3 documents");
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
            "xref\n0 4\n" +
            "0000000000 65535 f\n" +
            "0000000009 00000 n\n" +
            "0000000058 00000 n\n" +
            "0000000115 00000 n\n" +
            "trailer<</Size 4/Root 1 0 R>>\n" +
            "startxref\n" +
            "190\n" +
            "%%EOF";
        return Base64.getEncoder().encodeToString(pdf.getBytes(StandardCharsets.UTF_8));
    }

    private static String createDocxContent() {
        byte[] docxBytes = new byte[] {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00, 0x08, 0x00};
        return Base64.getEncoder().encodeToString(docxBytes);
    }

    private static String createTxtContent() {
        return Base64.getEncoder().encodeToString(
            "This is a test document content.\nLine 2 of the test document.\nLine 3 of the test document.".getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String createDocumentJson(String id, String filename, String content, String contentType, long size, boolean isZip, String origin) {
        return String.format(
            "{\"documentId\":\"%s\",\"filename\":\"%s\",\"content\":\"%s\",\"contentType\":\"%s\",\"size\":%d,\"isZip\":%b,\"origin\":\"%s\"}",
            id, filename, content, contentType, size, isZip, origin
        );
    }

    static class DocumentsListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            System.out.println("[" + method + "] " + path);

            if (!"GET".equalsIgnoreCase(method)) {
                sendJsonResponse(exchange, 405, "{\"error\":\"METHOD_NOT_ALLOWED\",\"message\":\"Only GET is supported\"}");
                return;
            }

            String doc1 = createDocumentJson("doc-001", "test-document.pdf", PDF_CONTENT, "application/pdf", PDF_CONTENT.length(), false, "folderA/incoming");
            String doc2 = createDocumentJson("doc-002", "test-document.docx", DOCX_CONTENT, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", DOCX_CONTENT.length(), false, "folderB/incoming");
            String doc3 = createDocumentJson("doc-003", "test-document.txt", TXT_CONTENT, "text/plain", TXT_CONTENT.length(), false, "folderA/special");

            String jsonResponse = "[" + doc1 + "," + doc2 + "," + doc3 + "]";
            sendJsonResponse(exchange, 200, jsonResponse);
            System.out.println("Response sent: document list with 3 documents");
        }
    }

    static class DocumentByIdHandler implements HttpHandler {
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
            String documentId = path.substring(pathInfo.length() + 1);
            documentId = URLDecoder.decode(documentId, StandardCharsets.UTF_8);

            if (documentId.isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"error\":\"BAD_REQUEST\",\"message\":\"Document ID is required\"}");
                return;
            }

            System.out.println("Document requested: " + documentId);

            String jsonResponse = switch (documentId) {
                case "doc-001" -> createDocumentJson("doc-001", "test-document.pdf", PDF_CONTENT, "application/pdf", PDF_CONTENT.length(), false, "folderA/incoming");
                case "doc-002" -> createDocumentJson("doc-002", "test-document.docx", DOCX_CONTENT, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", DOCX_CONTENT.length(), false, "folderB/incoming");
                case "doc-003" -> createDocumentJson("doc-003", "test-document.txt", TXT_CONTENT, "text/plain", TXT_CONTENT.length(), false, "folderA/special");
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
