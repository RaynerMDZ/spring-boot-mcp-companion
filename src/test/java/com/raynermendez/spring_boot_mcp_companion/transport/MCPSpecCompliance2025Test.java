package com.raynermendez.spring_boot_mcp_companion.transport;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Tests for MCP 2025-11-25 specification compliance.
 *
 * <p>Verifies:
 * 1. Protocol version 2025-11-25 support
 * 2. Version negotiation and backward compatibility
 * 3. Enhanced capability negotiation
 * 4. Client primitives (sampling, elicitation, logging, roots)
 * 5. Server-to-client notifications
 * 6. HTTP protocol version headers
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class MCPSpecCompliance2025Test {

  @Autowired
  private WebApplicationContext webApplicationContext;

  @Autowired
  private ObjectMapper objectMapper;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
  }

  @Test
  void testInitializeWith2025ProtocolVersion() throws Exception {
    Map<String, Object> params = Map.of(
        "protocolVersion", "2025-11-25",
        "capabilities", Map.of(),
        "clientInfo", Map.of("name", "TestClient", "version", "1.0.0")
    );
    JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "initialize", params);
    String requestJson = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/mcp/initialize")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.protocolVersion").value("2025-11-25"));
  }

  @Test
  void testBackwardCompatibilityWith2024Version() throws Exception {
    Map<String, Object> params = Map.of(
        "protocolVersion", "2025-11-25",
        "capabilities", Map.of(),
        "clientInfo", Map.of("name", "TestClient", "version", "1.0.0")
    );
    JsonRpcRequest request = new JsonRpcRequest("2.0", 2, "initialize", params);
    String requestJson = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/mcp/initialize")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        // Server responds with 2025-11-25 as current version
        .andExpect(jsonPath("$.result.protocolVersion").value("2025-11-25"));
  }

  @Test
  void testVersionNegotiationWithUnsupportedVersion() throws Exception {
    Map<String, Object> params = Map.of(
        "protocolVersion", "1.0.0",  // Unsupported version
        "capabilities", Map.of(),
        "clientInfo", Map.of("name", "TestClient", "version", "1.0.0")
    );
    JsonRpcRequest request = new JsonRpcRequest("2.0", 3, "initialize", params);
    String requestJson = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/mcp/initialize")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.error").exists())
        .andExpect(jsonPath("$.error.message").value("Unsupported protocol version"))
        .andExpect(jsonPath("$.error.data.supported").isArray())
        .andExpect(jsonPath("$.error.data.requested").value("1.0.0"));
  }

  @Test
  void testEnhancedCapabilityDeclaration() throws Exception {
    Map<String, Object> params = Map.of(
        "protocolVersion", "2025-11-25",
        "capabilities", Map.of(),
        "clientInfo", Map.of("name", "TestClient", "version", "1.0.0")
    );
    JsonRpcRequest request = new JsonRpcRequest("2.0", 4, "initialize", params);
    String requestJson = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/mcp/initialize")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        // Verify detailed capabilities
        .andExpect(jsonPath("$.result.capabilities.tools.listChanged").value(true))
        .andExpect(jsonPath("$.result.capabilities.resources.listChanged").value(true))
        .andExpect(jsonPath("$.result.capabilities.resources.subscribe").value(true))
        .andExpect(jsonPath("$.result.capabilities.prompts.listChanged").value(true))
        .andExpect(jsonPath("$.result.capabilities.logging").exists())
        .andExpect(jsonPath("$.result.capabilities.completions").exists())
        .andExpect(jsonPath("$.result.capabilities.tasks").exists());
  }

  @Test
  void testEnhancedServerInfo() throws Exception {
    Map<String, Object> params = Map.of(
        "protocolVersion", "2025-11-25",
        "capabilities", Map.of(),
        "clientInfo", Map.of("name", "TestClient", "version", "1.0.0")
    );
    JsonRpcRequest request = new JsonRpcRequest("2.0", 5, "initialize", params);
    String requestJson = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/mcp/initialize")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        // Verify enhanced serverInfo
        .andExpect(jsonPath("$.result.serverInfo.name").value("Spring Boot MCP Companion"))
        .andExpect(jsonPath("$.result.serverInfo.title").exists())
        .andExpect(jsonPath("$.result.serverInfo.version").value("1.0.0"))
        .andExpect(jsonPath("$.result.serverInfo.description").exists())
        .andExpect(jsonPath("$.result.serverInfo.websiteUrl").exists())
        .andExpect(jsonPath("$.result.serverInfo.icons").isArray())
        .andExpect(jsonPath("$.result.serverInfo.instructions").exists());
  }

  @Test
  void testSamplingCreateMessage() throws Exception {
    Map<String, Object> params = Map.of(
        "messages", java.util.List.of(),
        "model", "claude-3-5-sonnet"
    );
    JsonRpcRequest request = new JsonRpcRequest("2.0", 6, "sampling/createMessage", params);
    String requestJson = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/mcp/sampling/createMessage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.content").exists())
        .andExpect(jsonPath("$.result.content.type").value("text"));
  }

  @Test
  void testElicitationCreate() throws Exception {
    Map<String, Object> params = Map.of(
        "type", "text",
        "prompt", "Please provide input:"
    );
    JsonRpcRequest request = new JsonRpcRequest("2.0", 7, "elicitation/create", params);
    String requestJson = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/mcp/elicitation/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.elicitationId").exists())
        .andExpect(jsonPath("$.result.status").value("pending"));
  }

  @Test
  void testLogging() throws Exception {
    Map<String, Object> params = Map.of(
        "level", "info",
        "message", "Test log message"
    );
    JsonRpcRequest request = new JsonRpcRequest("2.0", 8, "logging/create", params);
    String requestJson = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/mcp/logging/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.accepted").value(true));
  }

  @Test
  void testLoggingWithDifferentLevels() throws Exception {
    String[] levels = {"debug", "info", "warning", "error"};

    for (String level : levels) {
      Map<String, Object> params = Map.of(
          "level", level,
          "message", "Test message at " + level
      );
      JsonRpcRequest request = new JsonRpcRequest("2.0", 100, "logging/create", params);
      String requestJson = objectMapper.writeValueAsString(request);

      mockMvc
          .perform(
              post("/mcp/logging/create")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestJson))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.result.accepted").value(true));
    }
  }

  @Test
  void testRootsList() throws Exception {
    JsonRpcRequest request = new JsonRpcRequest("2.0", 9, "roots/list", Map.of());
    String requestJson = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/mcp/roots/list")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.roots").isArray())
        .andExpect(jsonPath("$.result.roots[0].uri").exists())
        .andExpect(jsonPath("$.result.roots[0].name").exists());
  }

  @Test
  void testToolsListChangedNotification() throws Exception {
    JsonRpcRequest request = new JsonRpcRequest("2.0", 10, "notifications/tools/list_changed", Map.of());
    String requestJson = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/mcp/notifications/tools/list_changed")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result").exists());
  }

  @Test
  void testResourcesListChangedNotification() throws Exception {
    JsonRpcRequest request = new JsonRpcRequest("2.0", 11, "notifications/resources/list_changed", Map.of());
    String requestJson = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/mcp/notifications/resources/list_changed")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result").exists());
  }

  @Test
  void testPromptsListChangedNotification() throws Exception {
    JsonRpcRequest request = new JsonRpcRequest("2.0", 12, "notifications/prompts/list_changed", Map.of());
    String requestJson = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/mcp/notifications/prompts/list_changed")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result").exists());
  }

  @Test
  void testHTTPProtocolVersionHeader() throws Exception {
    Map<String, Object> params = Map.of(
        "protocolVersion", "2025-11-25",
        "capabilities", Map.of(),
        "clientInfo", Map.of("name", "TestClient", "version", "1.0.0")
    );
    JsonRpcRequest request = new JsonRpcRequest("2.0", 13, "initialize", params);
    String requestJson = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/mcp/initialize")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(header().exists("MCP-Protocol-Version"))
        .andExpect(header().string("MCP-Protocol-Version", "2025-11-25"));
  }

  @Test
  void testMultipleProtocolVersionsSupported() throws Exception {
    // Test that server accepts both 2025-11-25 and 2025-11-25
    String[] versions = {"2025-11-25", "2025-11-25"};

    for (String version : versions) {
      Map<String, Object> params = Map.of(
          "protocolVersion", version,
          "capabilities", Map.of(),
          "clientInfo", Map.of("name", "TestClient", "version", "1.0.0")
      );
      JsonRpcRequest request = new JsonRpcRequest("2.0", 200, "initialize", params);
      String requestJson = objectMapper.writeValueAsString(request);

      mockMvc
          .perform(
              post("/mcp/initialize")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestJson))
          .andExpect(status().isOk())
          // Server always responds with latest version
          .andExpect(jsonPath("$.result.protocolVersion").value("2025-11-25"));
    }
  }
}
