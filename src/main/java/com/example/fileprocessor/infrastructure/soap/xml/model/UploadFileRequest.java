package com.example.fileprocessor.infrastructure.soap.xml.model;

import com.example.fileprocessor.infrastructure.soap.xml.SoapNamespaces;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "UploadFileRequest", namespace = SoapNamespaces.FILE_SERVICE)
@XmlAccessorType(XmlAccessType.FIELD)
public class UploadFileRequest {

    @XmlElement(name = "content", namespace = SoapNamespaces.FILE_SERVICE, required = true)
    private String content;

    @XmlElement(name = "filename", namespace = SoapNamespaces.FILE_SERVICE, required = true)
    private String filename;

    @XmlElement(name = "contentType", namespace = SoapNamespaces.FILE_SERVICE, required = true)
    private String contentType;

    @XmlElement(name = "fileSize", namespace = SoapNamespaces.FILE_SERVICE)
    private long fileSize;

    @XmlElement(name = "traceId", namespace = SoapNamespaces.FILE_SERVICE, required = true)
    private String traceId;

    @XmlElement(name = "timestamp", namespace = SoapNamespaces.FILE_SERVICE, required = true)
    private String timestamp;

    public UploadFileRequest() {
    }

    public UploadFileRequest(String content, String filename, String contentType,
                             long fileSize, String traceId, String timestamp) {
        this.content = content;
        this.filename = filename;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.traceId = traceId;
        this.timestamp = timestamp;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
