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
            true
        );

        assertEquals("my-bucket", props.bucketName());
        assertEquals("us-east-1", props.region());
        assertEquals("http://localhost:9000", props.endpoint());
        assertTrue(props.pathStyleAccess());
    }

    @Test
    void getters_shouldReturnCorrectValues() {
        S3Properties props = new S3Properties(
            "production-bucket",
            "eu-west-1",
            null,
            false
        );

        assertEquals("production-bucket", props.bucketName());
        assertEquals("eu-west-1", props.region());
        assertNull(props.endpoint());
        assertFalse(props.pathStyleAccess());
    }

    @Test
    void equals_sameValues_shouldBeEqual() {
        S3Properties props1 = new S3Properties("bucket", "us-east-1", null, false);
        S3Properties props2 = new S3Properties("bucket", "us-east-1", null, false);

        assertEquals(props1, props2);
    }

    @Test
    void toString_shouldContainValues() {
        S3Properties props = new S3Properties("bucket", "us-east-1", null, false);

        String str = props.toString();
        assertNotNull(str);
        assertTrue(str.contains("bucket"));
    }
}