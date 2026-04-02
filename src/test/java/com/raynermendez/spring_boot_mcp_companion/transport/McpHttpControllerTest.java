package com.raynermendez.spring_boot_mcp_companion.transport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.raynermendez.spring_boot_mcp_companion.session.McpSession;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raynermendez.spring_boot_mcp_companion.config.McpServerProperties;
import com.raynermendez.spring_boot_mcp_companion.dispatch.McpDispatcher;
import com.raynermendez.spring_boot_mcp_companion.dispatch.McpDispatcher.McpContent;
import com.raynermendez.spring_boot_mcp_companion.dispatch.McpDispatcher.McpResourceResult;
import com.raynermendez.spring_boot_mcp_companion.dispatch.McpDispatcher.McpToolResult;
import com.raynermendez.spring_boot_mcp_companion.model.McpResourceDefinition;
import com.raynermendez.spring_boot_mcp_companion.model.McpToolDefinition;
import com.raynermendez.spring_boot_mcp_companion.notification.SseNotificationManager;
import com.raynermendez.spring_boot_mcp_companion.registry.McpDefinitionRegistry;
import com.raynermendez.spring_boot_mcp_companion.security.ErrorMessageSanitizer;
import com.raynermendez.spring_boot_mcp_companion.session.McpSessionManager;
import com.raynermendez.spring_boot_mcp_companion.transport.JsonRpcRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Tests for McpHttpController implementing MCP 2025-11-25 specification.
 */
@ExtendWith(MockitoExtension.class)
class McpHttpControllerTest {

    @Mock private McpDispatcher dispatcher;
    @Mock private McpDefinitionRegistry registry;
    @Mock private ErrorMessageSanitizer errorSanitizer;
    @Mock private SseNotificationManager notificationManager;
    @Mock private McpSessionManager sessionManager;

    private McpHttpController controller;
    private ObjectMapper objectMapper;
    private McpServerProperties properties;
    private HttpStatusMapper statusMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new McpServerProperties(true, "test-server", "1.0.0", "/mcp", "2025-11-25");
        statusMapper = new HttpStatusMapper();

        controller = new McpHttpController(
            dispatcher,
            registry,
            properties,
            errorSanitizer,
            objectMapper,
            notificationManager,
            sessionManager,
            statusMapper,
            mock(com.raynermendez.spring_boot_mcp_companion.prompt.PromptArgumentValidator.class)
        );

        // Stub createSession to return a real McpSession (lenient to avoid unused stubbing errors)
        McpSession mockSession = new McpSession("test-session-id", "2025-11-25", Map.of(), Map.of());
        lenient().when(sessionManager.createSession(any(), any(), any())).thenReturn(mockSession);
    }

    @Test
    void testInitializeRequest_Success() throws Exception {
        // Arrange
        String requestBody = """
            {
              "jsonrpc": "2.0",
              "id": "1",
              "method": "initialize",
              "params": {
                "protocolVersion": "2025-11-25",
                "clientInfo": {"name": "test-client", "version": "1.0"}
              }
            }
            """;

        MockHttpServletRequest httpRequest = new MockHttpServletRequest("POST", "/mcp");
        httpRequest.setMethod("POST");

        // Act
        ResponseEntity<?> response = controller.handleMcpRequest(
            httpRequest, "application/json", "application/json", null, null, null, requestBody
        );

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getHeaders().getFirst("MCP-Session-Id"));
        assertEquals("2025-11-25", response.getHeaders().getFirst("MCP-Protocol-Version"));
    }

    @Test
    void testInitializeRequest_DifferentVersion_RespondsWithServerVersion() throws Exception {
        // Arrange - per MCP spec, server MUST respond with its own supported version,
        // not reject the client. The client then decides whether to disconnect.
        String requestBody = """
            {
              "jsonrpc": "2.0",
              "id": "1",
              "method": "initialize",
              "params": {
                "protocolVersion": "2025-06-18",
                "clientInfo": {"name": "test-client", "version": "1.0"}
              }
            }
            """;

        MockHttpServletRequest httpRequest = new MockHttpServletRequest("POST", "/mcp");
        httpRequest.setMethod("POST");

        // Act
        ResponseEntity<?> response = controller.handleMcpRequest(
            httpRequest, "application/json", "application/json", null, null, null, requestBody
        );

        // Assert: server responds 200 OK with its own protocol version
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("2025-11-25", response.getHeaders().getFirst("MCP-Protocol-Version"));
    }

    @Test
    void testToolsList_Success() throws Exception {
        // Arrange
        when(registry.getTools()).thenReturn(List.of());
        when(sessionManager.getSession(any())).thenReturn(java.util.Optional.of(
            new com.raynermendez.spring_boot_mcp_companion.session.McpSession(
                "session-123", "2025-11-25", Map.of(), Map.of()
            )
        ));

        String requestBody = """
            {
              "jsonrpc": "2.0",
              "id": "2",
              "method": "tools/list",
              "params": {}
            }
            """;

        MockHttpServletRequest httpRequest = new MockHttpServletRequest("POST", "/mcp");
        httpRequest.setMethod("POST");

        // Act
        ResponseEntity<?> response = controller.handleMcpRequest(
            httpRequest, "application/json", "application/json", "session-123", null, null, requestBody
        );

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testInvalidJsonRpcRequest() throws Exception {
        // Arrange - Missing required id field still parses but response will use null id
        String requestBody = """
            {
              "jsonrpc": "2.0",
              "id": "1",
              "method": "invalid/method"
            }
            """;

        MockHttpServletRequest httpRequest = new MockHttpServletRequest("POST", "/mcp");
        httpRequest.setMethod("POST");
        when(sessionManager.getSession(any())).thenReturn(java.util.Optional.of(
            new com.raynermendez.spring_boot_mcp_companion.session.McpSession(
                "session-123", "2025-11-25", Map.of(), Map.of()
            )
        ));

        // Act
        ResponseEntity<?> response = controller.handleMcpRequest(
            httpRequest, "application/json", "application/json", "session-123", null, null, requestBody
        );

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void testExpiredSession() throws Exception {
        // Arrange
        when(sessionManager.getSession("expired-session")).thenReturn(java.util.Optional.empty());

        String requestBody = """
            {
              "jsonrpc": "2.0",
              "id": "3",
              "method": "tools/list",
              "params": {}
            }
            """;

        MockHttpServletRequest httpRequest = new MockHttpServletRequest("POST", "/mcp");
        httpRequest.setMethod("POST");

        // Act
        ResponseEntity<?> response = controller.handleMcpRequest(
            httpRequest, "application/json", "application/json", "expired-session", null, null, requestBody
        );

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void testMethodNotFound() throws Exception {
        // Arrange
        when(sessionManager.getSession(any())).thenReturn(java.util.Optional.of(
            new com.raynermendez.spring_boot_mcp_companion.session.McpSession(
                "session-123", "2025-11-25", Map.of(), Map.of()
            )
        ));

        String requestBody = """
            {
              "jsonrpc": "2.0",
              "id": "4",
              "method": "invalid/method",
              "params": {}
            }
            """;

        MockHttpServletRequest httpRequest = new MockHttpServletRequest("POST", "/mcp");
        httpRequest.setMethod("POST");

        // Act
        ResponseEntity<?> response = controller.handleMcpRequest(
            httpRequest, "application/json", "application/json", "session-123", null, null, requestBody
        );

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void testParseError_InvalidJson() throws Exception {
        // Arrange
        String requestBody = "invalid json";

        MockHttpServletRequest httpRequest = new MockHttpServletRequest("POST", "/mcp");
        httpRequest.setMethod("POST");

        // Act
        ResponseEntity<?> response = controller.handleMcpRequest(
            httpRequest, "application/json", "application/json", null, null, null, requestBody
        );

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
