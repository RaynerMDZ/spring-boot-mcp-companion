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

/** Integration tests for McpTransportController via MockMvc. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class McpTransportControllerTest {

  @Autowired
  private WebApplicationContext webApplicationContext;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private DefaultMcpDefinitionRegistry registry;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() throws NoSuchMethodException {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

    // Only register additional tools if registry is not yet locked
    // The scanner will have already registered the sample service tool
    // We'll just use what's already registered
  }

  @Test
  void testInitializeReturnsProtocolVersionAndCapabilities() throws Exception {
    JsonRpcRequest request = new JsonRpcRequest("2.0", 0, "initialize", Map.of());
    String requestJson = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/mcp/initialize")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jsonrpc").value("2.0"))
        .andExpect(jsonPath("$.id").value(0))
        .andExpect(jsonPath("$.result.protocolVersion").value("2024-11-05"))
        .andExpect(jsonPath("$.result.capabilities.tools").exists())
        .andExpect(jsonPath("$.result.capabilities.resources").exists())
        .andExpect(jsonPath("$.result.capabilities.prompts").exists())
        .andExpect(jsonPath("$.result.serverInfo.name").value("Spring Boot MCP Companion"))
        .andExpect(jsonPath("$.result.serverInfo.version").value("1.0.0"));
  }

  @Test
  void testToolsListReturnsValidJsonRpcResponse() throws Exception {
    JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "tools/list", Map.of());
    String requestJson = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/mcp/tools/list")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jsonrpc").value("2.0"))
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.result.tools", hasSize(greaterThanOrEqualTo(1))));
  }

  @Test
  void testCallToolWithValidNameReturnsResult() throws Exception {
    Map<String, Object> params = Map.of("name", "get_order", "arguments", Map.of("orderId", "ORDER-001"));
    JsonRpcRequest request = new JsonRpcRequest("2.0", 2, "tools/call", params);
    String requestJson = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/mcp/tools/call")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jsonrpc").value("2.0"))
        .andExpect(jsonPath("$.id").value(2))
        .andExpect(jsonPath("$.result.content", hasSize(1)))
        .andExpect(jsonPath("$.result.content[0].type").value("text"));
  }

  @Test
  void testCallToolWithUnknownNameReturnsError() throws Exception {
    Map<String, Object> params = Map.of("name", "unknown_tool", "arguments", Map.of());
    JsonRpcRequest request = new JsonRpcRequest("2.0", 3, "tools/call", params);
    String requestJson = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/mcp/tools/call")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jsonrpc").value("2.0"))
        .andExpect(jsonPath("$.id").value(3))
        .andExpect(jsonPath("$.error").exists())
        .andExpect(jsonPath("$.error.code").value(-32601));
  }

  @Test
  void testCallToolWithoutNameParameterReturnsInvalidParamsError() throws Exception {
    Map<String, Object> params = Map.of("arguments", Map.of());
    JsonRpcRequest request = new JsonRpcRequest("2.0", 4, "tools/call", params);
    String requestJson = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/mcp/tools/call")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jsonrpc").value("2.0"))
        .andExpect(jsonPath("$.id").value(4))
        .andExpect(jsonPath("$.error").exists())
        .andExpect(jsonPath("$.error.code").value(-32602));
  }

  @Test
  void testToolsListEndpointIsAtConfiguredBasePath() throws Exception {
    JsonRpcRequest request = new JsonRpcRequest("2.0", 5, "tools/list", Map.of());
    String requestJson = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/mcp/tools/list")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jsonrpc").value("2.0"));
  }

  @Test
  void testCallToolEndpointIsAtConfiguredBasePath() throws Exception {
    Map<String, Object> params = Map.of("name", "get_order", "arguments", Map.of("orderId", "ORDER-001"));
    JsonRpcRequest request = new JsonRpcRequest("2.0", 6, "tools/call", params);
    String requestJson = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/mcp/tools/call")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jsonrpc").value("2.0"));
  }

}
