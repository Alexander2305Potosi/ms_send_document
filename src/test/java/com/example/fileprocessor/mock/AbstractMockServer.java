package com.example.fileprocessor.mock;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Base class for mock servers. Provides common utilities for port resolution,
 * server info persistence, and lifecycle management.
 */
public abstract class AbstractMockServer {

    protected static final int PORT_RANGE_START;
    protected static final int PORT_RANGE_END;
    protected static final String INFO_FILE_NAME = "file-processor-mock.info";

    static {
        // Default ranges - subclasses can override
        PORT_RANGE_START = 8000;
        PORT_RANGE_END = 9999;
    }

    protected final int port;
    protected final String name;

    protected AbstractMockServer(String name, int defaultPort) {
        this.name = name;
        this.port = resolvePort(defaultPort);
    }

    protected int resolvePort(int defaultPort) {
        int port = findAvailablePort(defaultPort);
        System.out.println(name + " usando puerto: " + port);
        return port;
    }

    private int findAvailablePort(int preferred) {
        if (isPortAvailable(preferred)) {
            return preferred;
        }
        for (int p = PORT_RANGE_START; p <= PORT_RANGE_END; p++) {
            if (isPortAvailable(p)) {
                System.out.println(name + " puerto preferido " + preferred + " ocupado, usando: " + p);
                return p;
            }
        }
        throw new RuntimeException("No se encontro puerto disponible en rango " + PORT_RANGE_START + "-" + PORT_RANGE_END);
    }

    protected boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    protected void saveServerInfo() throws IOException {
        Path infoFile = getServerInfoFile();
        String content = "port=" + port + "\n" +
                        "timestamp=" + System.currentTimeMillis() + "\n";
        Files.writeString(infoFile, content);
    }

    protected void cleanupServerInfo() {
        try {
            Files.deleteIfExists(getServerInfoFile());
        } catch (IOException e) {
            // Ignorar
        }
    }

    protected Path getServerInfoFile() {
        String tempDir = System.getProperty("java.io.tmpdir");
        return Paths.get(tempDir, INFO_FILE_NAME);
    }

    protected void waitForShutdown() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nDeteniendo " + name + "...");
            cleanupServerInfo();
        }));

        synchronized (AbstractMockServer.class) {
            try {
                AbstractMockServer.class.wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    protected void printBanner() {
        System.out.println();
        System.out.println("========================================");
        System.out.println("  " + name);
        System.out.println("========================================");
        System.out.println("  Puerto: " + port);
        System.out.println("========================================");
        System.out.println();
    }

    public int getPort() {
        return port;
    }

    public String getEndpoint() {
        return getBaseUrl() + getContextPath();
    }

    protected abstract String getBaseUrl();
    protected abstract String getContextPath();
}
