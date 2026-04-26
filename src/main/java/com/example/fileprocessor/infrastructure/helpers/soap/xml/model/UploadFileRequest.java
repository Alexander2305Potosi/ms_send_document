package com.example.fileprocessor.infrastructure.helpers.soap.xml.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "UploadFileRequest", namespace = "http://example.com/fileservice")
@XmlAccessorType(XmlAccessType.FIELD)
public class UploadFileRequest {

    @XmlElement(name = "fileContentBase64")
    private String fileContentBase64;

    @XmlElement(name = "filename")
    private String filename;

    @XmlElement(name = "contentType")
    private String contentType;

    @XmlElement(name = "fileSize")
    private long fileSize;

    @XmlElement(name = "traceId")
    private String traceId;

    @XmlElement(name = "timestamp")
    private String timestamp;

    @XmlElement(name = "parentFolder")
    private String parentFolder;

    @XmlElement(name = "childFolder")
    private String childFolder;

    public UploadFileRequest() {}

    public UploadFileRequest(String fileContentBase64, String filename, String contentType,
                           long fileSize, String traceId, String timestamp,
                           String parentFolder, String childFolder) {
        this.fileContentBase64 = fileContentBase64;
        this.filename = filename;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.traceId = traceId;
        this.timestamp = timestamp;
        this.parentFolder = parentFolder;
        this.childFolder = childFolder;
    }

    public String getFileContentBase64() { return fileContentBase64; }
    public String getFilename() { return filename; }
    public String getContentType() { return contentType; }
    public long getFileSize() { return fileSize; }
    public String getTraceId() { return traceId; }
    public String getTimestamp() { return timestamp; }
    public String getParentFolder() { return parentFolder; }
    public String getChildFolder() { return childFolder; }
}