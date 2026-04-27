package com.example.fileprocessor.infrastructure.drivenadapters.aws.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class S3PropertiesTest {

    @Test
    void record_shouldCreateInstance() {
        S3Properties props = new S3Properties(
            "my-bucket",
            "us-east-1",
            "http://localhost:9000",
            true,
            null,
            null
        );

        assertEquals("my-bucket", props.bucketName());
        assertEquals("us-east-1", props.region());
        assertEquals("http://localhost:9000", props.endpoint());
        assertTrue(props.pathStyleAccess());
        assertNull(props.accessKey());
        assertNull(props.secretKey());
    }

    @Test
    void getters_shouldReturnCorrectValues() {
        S3Properties props = new S3Properties(
            "production-bucket",
            "eu-west-1",
            null,
            false,
            "my-access-key",
            "my-secret-key"
        );

        assertEquals("production-bucket", props.bucketName());
        assertEquals("eu-west-1", props.region());
        assertNull(props.endpoint());
        assertFalse(props.pathStyleAccess());
        assertEquals("my-access-key", props.accessKey());
        assertEquals("my-secret-key", props.secretKey());
    }

    @Test
    void equals_sameValues_shouldBeEqual() {
        S3Properties props1 = new S3Properties("bucket", "us-east-1", null, false, null, null);
        S3Properties props2 = new S3Properties("bucket", "us-east-1", null, false, null, null);

        assertEquals(props1, props2);
    }

    @Test
    void toString_shouldContainValues() {
        S3Properties props = new S3Properties("bucket", "us-east-1", null, false, null, null);

        String str = props.toString();
        assertNotNull(str);
        assertTrue(str.contains("bucket"));
    }
}