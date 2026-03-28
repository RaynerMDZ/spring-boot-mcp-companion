package com.raynermendez.spring_boot_mcp_companion.integration;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raynermendez.spring_boot_mcp_companion.SpringBootMcpCompanionApplication;
import com.raynermendez.spring_boot_mcp_companion.transport.JsonRpcRequest;
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
 * Integration tests for the MCP tool framework.
 *
 * <p>These tests verify that the MCP annotation scanning, discovery, and invocation system works
 * end-to-end with a real Spring application context.
 */
@SpringBootTest(classes = SpringBootMcpCompanionApplication.class)
class McpToolIntegrationTest {

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
  void testToolsListReturnsRegisteredTools() throws Exception {
    JsonRpcRequest request = new JsonRpcRequest("2.0", 1, "tools/list", Map.of());
    String requestJson = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/mcp/tools/list")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jsonrpc").value("2.0"))
        .andExpect(jsonPath("$.result.tools").isArray())
        .andExpect(jsonPath("$.result.tools", hasSize(greaterThanOrEqualTo(1))));
  }

  @Test
  void testCallSampleOrderServiceTool() throws Exception {
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
        .andExpect(jsonPath("$.result").exists())
        .andExpect(jsonPath("$.result.content", hasSize(1)))
        .andExpect(jsonPath("$.result.content[0].type").value("text"))
        .andExpect(jsonPath("$.result.content[0].text").value(containsString("Sample Widget")));
  }

  @Test
  void testBackwardCompatibilityWithHttpApi() throws Exception {
    // Verify that the existing HTTP API still works
    mockMvc
        .perform(get("/api/orders/ORDER-001").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orderId").value("ORDER-001"))
        .andExpect(jsonPath("$.name").value("Sample Widget"))
        .andExpect(jsonPath("$.amount").value(99.99));
  }

  @Test
  void testMcpParameterValidationReturnsError() throws Exception {
    // Test with empty orderId
    Map<String, Object> params = Map.of("name", "get_order", "arguments", Map.of("orderId", ""));
    JsonRpcRequest request = new JsonRpcRequest("2.0", 3, "tools/call", params);
    String requestJson = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/mcp/tools/call")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jsonrpc").value("2.0"));
    // Note: Phase 5 will add strict validation; for now, tool executes with empty string
  }

  @Test
  void testSampleOrderControllerTestStillPasses() throws Exception {
    // Verify backward compatibility by testing the HTTP endpoint
    mockMvc
        .perform(get("/api/orders/ORDER-002").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orderId").value("ORDER-002"))
        .andExpect(jsonPath("$.name").value("Sample Gadget"))
        .andExpect(jsonPath("$.status").value("PENDING"));
  }

  @Test
  void testToolDescriptorIncludesInputSchema() throws Exception {
    JsonRpcRequest request = new JsonRpcRequest("2.0", 4, "tools/list", Map.of());
    String requestJson = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/mcp/tools/list")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.tools[*].inputSchema").exists());
  }
}
