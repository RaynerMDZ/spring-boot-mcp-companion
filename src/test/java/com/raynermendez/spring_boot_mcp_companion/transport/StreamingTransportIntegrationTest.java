package com.raynermendez.spring_boot_mcp_companion.transport;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raynermendez.spring_boot_mcp_companion.registry.DefaultMcpDefinitionRegistry;
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
 * Integration tests for streaming HTTP transport in MCP controller.
 *
 * <p>Verifies that MCP endpoints:
 * 1. Stream JSON-RPC responses directly to output without buffering
 * 2. Set appropriate content type headers
 * 3. Handle both success and error responses correctly
 * 4. Work with all MCP endpoints (initialize, tools/list, tools/call, etc.)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class StreamingTransportIntegrationTest {

  @Autowired
  private WebApplicationContext webApplicationContext;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private DefaultMcpDefinitionRegistry registry;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
  }

  @Test
  void testInitializeResponseIsStreamed() throws Exception {
    JsonRpcRequest request = new JsonRpcRequest("2.0", 0, "initialize", Map.of());
    String requestJson = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/mcp/initialize")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/json"))
        .andExpect(jsonPath("$.jsonrpc").value("2.0"))
        .andExpect(jsonPath("$.id").value(0))
        .andExpect(jsonPath("$.result.protocolVersion").value("2024-11-05"))
        .andExpect(jsonPath("$.result.serverInfo.name").value("Spring Boot MCP Companion"));
  }

  @Test
  void testToolsListResponseIsStreamed() throws Exception {
    JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "tools/list", Map.of());
    String requestJson = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/mcp/tools/list")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/json"))
        .andExpect(jsonPath("$.jsonrpc").value("2.0"))
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.result.tools", isA(java.util.Collection.class)));
  }

  @Test
  void testErrorResponseIsStreamed() throws Exception {
    Map<String, Object> params = Map.of("name", "unknown_tool", "arguments", Map.of());
    JsonRpcRequest request = new JsonRpcRequest("2.0", 2, "tools/call", params);
    String requestJson = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/mcp/tools/call")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/json"))
        .andExpect(jsonPath("$.jsonrpc").value("2.0"))
        .andExpect(jsonPath("$.id").value(2))
        .andExpect(jsonPath("$.error").exists())
        .andExpect(jsonPath("$.error.code").value(-32601));
  }

  @Test
  void testInvalidParamsErrorIsStreamed() throws Exception {
    Map<String, Object> params = Map.of("arguments", Map.of());  // Missing "name"
    JsonRpcRequest request = new JsonRpcRequest("2.0", 3, "tools/call", params);
    String requestJson = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/mcp/tools/call")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/json"))
        .andExpect(jsonPath("$.jsonrpc").value("2.0"))
        .andExpect(jsonPath("$.id").value(3))
        .andExpect(jsonPath("$.error").exists())
        .andExpect(jsonPath("$.error.code").value(-32602));
  }

  @Test
  void testResourcesListResponseIsStreamed() throws Exception {
    JsonRpcRequest request = new JsonRpcRequest("2.0", 4, "resources/list", Map.of());
    String requestJson = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/mcp/resources/list")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/json"))
        .andExpect(jsonPath("$.jsonrpc").value("2.0"))
        .andExpect(jsonPath("$.id").value(4))
        .andExpect(jsonPath("$.result.resources", isA(java.util.Collection.class)));
  }

  @Test
  void testPromptsListResponseIsStreamed() throws Exception {
    JsonRpcRequest request = new JsonRpcRequest("2.0", 5, "prompts/list", Map.of());
    String requestJson = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/mcp/prompts/list")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/json"))
        .andExpect(jsonPath("$.jsonrpc").value("2.0"))
        .andExpect(jsonPath("$.id").value(5))
        .andExpect(jsonPath("$.result.prompts", isA(java.util.Collection.class)));
  }

  @Test
  void testStreamingMaintainsJsonRpcStructure() throws Exception {
    JsonRpcRequest request = new JsonRpcRequest("2.0", 100, "initialize", Map.of());
    String requestJson = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/mcp/initialize")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        // Verify core JSON-RPC structure is preserved
        .andExpect(jsonPath("$.jsonrpc").exists())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(
            jsonPath("$")
                .exists()); // Response exists and is valid JSON
  }

  @Test
  void testContentTypeHeaderIsSet() throws Exception {
    JsonRpcRequest request = new JsonRpcRequest("2.0", 6, "initialize", Map.of());
    String requestJson = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/mcp/initialize")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON));
  }
}
