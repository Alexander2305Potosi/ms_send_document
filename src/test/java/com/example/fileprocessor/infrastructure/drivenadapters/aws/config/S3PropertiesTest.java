package com.example.fileprocessor.infrastructure.drivenadapters.aws.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class S3PropertiesTest {

    @Test
    void s3Properties_recordCreatesValidProperties() {
        S3Properties props = new S3Properties(
            "test-bucket",
            "us-east-1",
            "http://localhost:4566",
            true,
            "access-key",
            "secret-key",
            3,
            500,
            30,
            "documents/"
        );

        assertEquals("test-bucket", props.bucketName());
        assertEquals("us-east-1", props.region());
        assertEquals("http://localhost:4566", props.endpoint());
        assertTrue(props.pathStyleAccess());
        assertEquals("access-key", props.accessKey());
        assertEquals("secret-key", props.secretKey());
        assertEquals(3, props.retryAttempts());
        assertEquals(500, props.retryBackoffMillis());
        assertEquals(30, props.timeoutSeconds());
        assertEquals("documents/", props.keyPrefix());
    }

    @Test
    void s3Properties_withEmptyEndpoint() {
        S3Properties props = new S3Properties(
            "bucket", "region", "", false, "key", "secret", 3, 100, 10, "prefix/"
        );

        assertEquals("", props.endpoint());
    }
}
