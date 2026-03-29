package com.raynermendez.spring_boot_mcp_companion.connection;

/**
 * Represents the lifecycle state of an MCP connection.
 *
 * Connection states follow the MCP specification:
 * INIT -> INITIALIZING -> READY -> CLOSED
 *
 * @author Rayner Mendez
 */
public enum ConnectionState {
	/**
	 * Initial state when connection is first established.
	 * Client must send initialize request before any other operations.
	 */
	INIT("init"),

	/**
	 * Server has received initialize request and is processing it.
	 * Waiting for notifications/initialized from client.
	 */
	INITIALIZING("initializing"),

	/**
	 * Connection fully initialized and ready for all MCP operations.
	 * Can execute tools, read resources, get prompts, etc.
	 */
	READY("ready"),

	/**
	 * Connection is closed and no further operations are possible.
	 */
	CLOSED("closed");

	private final String value;

	ConnectionState(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	/**
	 * Validates if the given state transition is allowed.
	 *
	 * Valid transitions:
	 * - INIT -> INITIALIZING
	 * - INITIALIZING -> READY
	 * - Any state -> CLOSED
	 *
	 * @param fromState the current state
	 * @param toState the desired state
	 * @return true if transition is valid, false otherwise
	 */
	public static boolean isValidTransition(ConnectionState fromState, ConnectionState toState) {
		if (fromState == toState) {
			return false; // No self-transitions
		}

		if (toState == CLOSED) {
			return true; // Can close from any state
		}

		return switch (fromState) {
			case INIT -> toState == INITIALIZING;
			case INITIALIZING -> toState == READY;
			case READY, CLOSED -> false;
		};
	}
}
