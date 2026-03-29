package com.raynermendez.spring_boot_mcp_companion.config;

import com.raynermendez.spring_boot_mcp_companion.transport.McpWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket configuration for MCP protocol.
 *
 * Configures WebSocket endpoints for MCP client connections following
 * the MCP specification which requires persistent, stateful connections.
 *
 * @author Rayner Mendez
 */
@Configuration
@EnableWebSocket
public class McpWebSocketConfig implements WebSocketConfigurer {

	private final McpWebSocketHandler mcpWebSocketHandler;

	public McpWebSocketConfig(McpWebSocketHandler mcpWebSocketHandler) {
		this.mcpWebSocketHandler = mcpWebSocketHandler;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(mcpWebSocketHandler, "/mcp/connect")
				.setAllowedOrigins("*")
				.setAllowedOriginPatterns("*");
	}
}
