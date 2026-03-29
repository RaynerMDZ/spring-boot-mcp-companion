package com.raynermendez.spring_boot_mcp_companion.client;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles MCP client primitive requests.
 *
 * <p>MCP 2025-11-25 defines client primitives as capabilities that servers can use to:
 * <ul>
 *   <li>Sampling: Request language model completions from the client's AI application
 *   <li>Elicitation: Request additional information from users
 *   <li>Logging: Send log messages to the client for debugging
 * </ul>
 *
 * <p>In HTTP Streamable Transport, client primitives are implemented as:
 * <ul>
 *   <li>Server sends notification to connected clients
 *   <li>Client processes the request and responds in next interaction
 *   <li>No direct request-response for HTTP transport (unlike Stdio)
 * </ul>
 *
 * @author Rayner Mendez
 */
@Component
public class ClientPrimitiveRequestHandler {
    private static final Logger logger = LoggerFactory.getLogger(ClientPrimitiveRequestHandler.class);

    /**
     * Creates a sampling/complete request notification.
     *
     * <p>Asks the client (AI application) to perform language model sampling/completion.
     * This allows servers to request LLM completions without embedding an LLM SDK.
     *
     * @param model the model to use for completion
     * @param systemPrompt system prompt for the model
     * @param messages message history for context
     * @return JSON-RPC 2.0 notification for client
     */
    public Map<String, Object> createSamplingCompleteRequest(
        String model,
        String systemPrompt,
        java.util.List<Map<String, Object>> messages
    ) {
        Map<String, Object> params = new HashMap<>();
        params.put("model", model);
        params.put("systemPrompt", systemPrompt);
        params.put("messages", messages != null ? messages : java.util.List.of());

        logger.debug("Creating sampling/complete request: model={}", model);

        return createClientPrimitiveRequest("sampling/complete", params);
    }

    /**
     * Creates an elicitation/request notification.
     *
     * <p>Asks the client (user) to provide additional input or confirmation.
     * This allows servers to request user input for decision-making.
     *
     * @param type type of elicitation (e.g., "text", "confirmation", "selection")
     * @param title user-facing title for the elicitation
     * @param description description of what is being requested
     * @return JSON-RPC 2.0 notification for client
     */
    public Map<String, Object> createElicitationRequest(
        String type,
        String title,
        String description
    ) {
        Map<String, Object> params = new HashMap<>();
        params.put("type", type);
        params.put("title", title);
        params.put("description", description);

        logger.debug("Creating elicitation/request: type={}, title={}", type, title);

        return createClientPrimitiveRequest("elicitation/request", params);
    }

    /**
     * Creates a logging/message notification.
     *
     * <p>Sends a log message to the client for debugging and monitoring purposes.
     *
     * @param level log level (e.g., "debug", "info", "warn", "error")
     * @param message the log message
     * @return JSON-RPC 2.0 notification for client
     */
    public Map<String, Object> createLoggingMessage(
        String level,
        String message
    ) {
        Map<String, Object> params = new HashMap<>();
        params.put("level", level);
        params.put("message", message);

        logger.debug("Creating logging/message: level={}, message={}", level, message);

        return createClientPrimitiveRequest("logging/message", params);
    }

    /**
     * Creates a JSON-RPC 2.0 notification for client primitive request.
     *
     * @param method the primitive method name
     * @param params the parameters
     * @return JSON-RPC 2.0 notification
     */
    private Map<String, Object> createClientPrimitiveRequest(String method, Map<String, Object> params) {
        return Map.of(
            "jsonrpc", "2.0",
            "method", method,
            "params", params != null ? params : Map.of()
        );
    }

    /**
     * Validates that a client supports a specific primitive.
     *
     * @param clientCapabilities client capabilities from initialize
     * @param primitive primitive name (e.g., "sampling", "elicitation", "logging")
     * @return true if client declares support for this primitive
     */
    public boolean supportsClientPrimitive(Map<String, Object> clientCapabilities, String primitive) {
        return clientCapabilities != null && clientCapabilities.containsKey(primitive);
    }

    /**
     * Gets the capability configuration for a client primitive.
     *
     * @param clientCapabilities client capabilities from initialize
     * @param primitive primitive name
     * @return capability config, or empty map if not supported
     */
    public Map<String, Object> getClientPrimitiveCapability(
        Map<String, Object> clientCapabilities,
        String primitive
    ) {
        if (clientCapabilities == null || !clientCapabilities.containsKey(primitive)) {
            return Map.of();
        }

        Object capability = clientCapabilities.get(primitive);
        if (capability instanceof Map) {
            return (Map<String, Object>) capability;
        }

        return Map.of();
    }
}
