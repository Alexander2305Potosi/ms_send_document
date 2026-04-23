package com.example.fileprocessor.mock;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;

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
    private static final int PORT_RANGE_END = 8999;

    private static final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    record DocumentInfo(String documentId, String filename, String contentBase64,
                       String contentType, long size, Instant timestamp, boolean isZip) {}

    record ErrorResponse(String error, String message) {}

    // Sample document content (small test files)
    private static final String PDF_CONTENT = createPdfContent();
    private static final String DOCX_CONTENT = createDocxContent();
    private static final String TXT_CONTENT = createTxtContent();

    // Lazy initialized ZIP content
    private static String lazyZipContent;

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

    private static synchronized String getZipContent() throws IOException {
        if (lazyZipContent == null) {
            lazyZipContent = createZipContent();
        }
        return lazyZipContent;
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
        // Minimal DOCX structure (ZIP) - using bytes instead of escape sequences
        byte[] docxBytes = new byte[] {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00, 0x08, 0x00};
        return Base64.getEncoder().encodeToString(docxBytes);
    }

    private static String createTxtContent() {
        return Base64.getEncoder().encodeToString(
            "This is a test document content.\nLine 2 of the test document.\nLine 3 of the test document.".getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String createZipContent() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("inner-document.pdf"));
            zos.write("%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\nxref\n0 1\n0000000000 65535 f\ntrailer<</Size 1/Root 1 0 R>>\nstartxref\n50\n%%EOF".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("inner-document.txt"));
            zos.write("Document inside ZIP.\nLine 2 of inner document.".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("inner-document.docx"));
            byte[] docxBytes = new byte[] {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00, 0x08, 0x00};
            zos.write(docxBytes);
            zos.closeEntry();
        }
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    static class DocumentsListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            System.out.println("[" + method + "] " + path);

            if (!"GET".equalsIgnoreCase(method)) {
                sendJsonResponse(exchange, 405, new ErrorResponse("METHOD_NOT_ALLOWED", "Only GET is supported"));
                return;
            }

            try {
                String zipContent = getZipContent();
                byte[] zipBytes = Base64.getDecoder().decode(zipContent);

                List<DocumentInfo> documents = List.of(
                    new DocumentInfo("doc-001", "test-document.pdf", PDF_CONTENT,
                        "application/pdf", (long) PDF_CONTENT.getBytes(StandardCharsets.UTF_8).length, Instant.now(), false),
                    new DocumentInfo("doc-002", "test-document.docx", DOCX_CONTENT,
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", (long) DOCX_CONTENT.getBytes(StandardCharsets.UTF_8).length, Instant.now(), false),
                    new DocumentInfo("doc-003", "test-document.txt", TXT_CONTENT,
                        "text/plain", (long) TXT_CONTENT.getBytes(StandardCharsets.UTF_8).length, Instant.now(), false),
                    new DocumentInfo("doc-004", "documents.zip", zipContent,
                        "application/zip", (long) zipBytes.length, Instant.now(), true)
                );

                String jsonResponse = objectMapper.writeValueAsString(documents);
                sendJsonResponse(exchange, 200, jsonResponse);
                System.out.println("Response sent: document list with " + documents.size() + " documents");
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, new ErrorResponse("INTERNAL_ERROR", e.getMessage()));
            }
        }
    }

    static class DocumentByIdHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            System.out.println("[" + method + "] " + path);

            if (!"GET".equalsIgnoreCase(method)) {
                sendJsonResponse(exchange, 405, new ErrorResponse("METHOD_NOT_ALLOWED", "Only GET is supported"));
                return;
            }

            String pathInfo = exchange.getHttpContext().getPath();
            String documentId = path.substring(pathInfo.length() + 1);

            if (documentId.isEmpty()) {
                sendJsonResponse(exchange, 400, new ErrorResponse("BAD_REQUEST", "Document ID is required"));
                return;
            }

            System.out.println("Document requested: " + documentId);

            try {
                String zipContent = getZipContent();
                byte[] zipBytes = Base64.getDecoder().decode(zipContent);

                DocumentInfo document = switch (documentId) {
                    case "doc-001" -> new DocumentInfo("doc-001", "test-document.pdf", PDF_CONTENT,
                        "application/pdf", (long) PDF_CONTENT.getBytes(StandardCharsets.UTF_8).length, Instant.now(), false);
                    case "doc-002" -> new DocumentInfo("doc-002", "test-document.docx", DOCX_CONTENT,
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", (long) DOCX_CONTENT.getBytes(StandardCharsets.UTF_8).length, Instant.now(), false);
                    case "doc-003" -> new DocumentInfo("doc-003", "test-document.txt", TXT_CONTENT,
                        "text/plain", (long) TXT_CONTENT.getBytes(StandardCharsets.UTF_8).length, Instant.now(), false);
                    case "doc-004" -> new DocumentInfo("doc-004", "documents.zip", zipContent,
                        "application/zip", (long) zipBytes.length, Instant.now(), true);
                    default -> null;
                };

                if (document == null) {
                    sendJsonResponse(exchange, 404, new ErrorResponse("NOT_FOUND", "Document not found: " + documentId));
                } else {
                    String jsonResponse = objectMapper.writeValueAsString(document);
                    sendJsonResponse(exchange, 200, jsonResponse);
                    System.out.println("Response sent: document " + documentId);
                }
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, new ErrorResponse("INTERNAL_ERROR", e.getMessage()));
            }
        }
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, Object response) throws IOException {
        String json;
        if (response instanceof String s) {
            json = s;
        } else {
            json = objectMapper.writeValueAsString(response);
        }

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}