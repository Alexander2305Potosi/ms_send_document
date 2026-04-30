package com.example.fileprocessor.domain.usecase;

/**
 * Folder information extracted from a document's origin path.
 */
public record FolderInfo(String parentFolder, String childFolder) {

    public static FolderInfo root() {
        return new FolderInfo(".", ".");
    }
}
