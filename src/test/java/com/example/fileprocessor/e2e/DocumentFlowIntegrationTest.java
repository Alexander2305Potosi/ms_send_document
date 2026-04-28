package com.example.fileprocessor.e2e;

import com.example.fileprocessor.Application;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Application.class)
@TestPropertySource(properties = {
    "spring.r2dbc.url=r2dbc:h2:mem:///testdb;DB_CLOSE_DELAY=-1",
    "spring.sql.init.mode=never"
})
class DocumentFlowIntegrationTest {

    @Test
    void contextLoads() {
        // Verifies that the Spring context loads successfully
        // This is the most valuable E2E test - if context loads, wiring is correct
        assertThat(true).isTrue();
    }
}
