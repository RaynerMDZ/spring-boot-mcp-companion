package com.raynermendez.spring_boot_mcp_companion.annotation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link EnableMcpCompanion} annotation.
 *
 * Verifies that:
 * 1. The annotation enables MCP auto-configuration
 * 2. All required MCP beans are registered
 * 3. MCP endpoints are available
 */
@SpringBootTest(classes = EnableMcpCompanionTest.TestApplication.class)
@TestPropertySource(properties = "mcp.server.enabled=true")
class EnableMcpCompanionTest {

    /**
     * Test application with {@link EnableMcpCompanion} annotation.
     */
    @SpringBootApplication
    @EnableMcpCompanion
    static class TestApplication {
    }

    @Test
    void testEnableMcpCompanionAnnotationEnablesAutoConfiguration(ApplicationContext context) {
        // Verify MCP auto-configuration beans are registered
        assertTrue(context.containsBean("mcpDefinitionRegistry"),
                "McpDefinitionRegistry bean should be registered");
        assertTrue(context.containsBean("mcpMappingEngine"),
                "McpMappingEngine bean should be registered");
        assertTrue(context.containsBean("mcpAnnotationScanner"),
                "McpAnnotationScanner bean should be registered");
        assertTrue(context.containsBean("mcpDispatcher"),
                "McpDispatcher bean should be registered");
        assertTrue(context.containsBean("mcpInputValidator"),
                "McpInputValidator bean should be registered");
        assertTrue(context.containsBean("mcpTransportController"),
                "McpTransportController bean should be registered");
    }

    @Test
    void testMcpPropertiesBeanRegistered(ApplicationContext context) {
        // Check that at least one McpServerProperties bean is registered
        String[] beanNames = context.getBeanNamesForType(com.raynermendez.spring_boot_mcp_companion.config.McpServerProperties.class);
        assertTrue(beanNames.length > 0,
                "At least one McpServerProperties bean should be registered for configuration binding");
    }

    @Test
    void testAnnotationIsDocumented() {
        // Verify annotation itself is correctly configured
        assertTrue(EnableMcpCompanion.class.isAnnotation(),
                "EnableMcpCompanion should be an annotation");
        assertNotNull(EnableMcpCompanion.class.getAnnotation(java.lang.annotation.Documented.class),
                "EnableMcpCompanion should be @Documented");
    }
}
