package com.example.fileprocessor.domain.port.out;

/**
 * Port for resolving MIME types from filenames.
 * This decouples the domain from infrastructure libraries like Spring Web.
 */
public interface MimeTypeResolver {
    /**
     * Resolves the content type based on the filename.
     * @param filename The name of the file.
     * @return The MIME type string (e.g., "application/pdf").
     */
    String resolve(String filename);
}
