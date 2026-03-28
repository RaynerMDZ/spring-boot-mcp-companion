package com.raynermendez.spring_boot_mcp_companion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.raynermendez.spring_boot_mcp_companion.annotation.EnableMcpCompanion;

/**
 * Spring Boot MCP Companion Framework Bootstrap Application.
 *
 * This application demonstrates the MCP framework with:
 * - {@link EnableMcpCompanion} annotation to enable MCP server
 * - Sample annotated service ({@link com.raynermendez.spring_boot_mcp_companion.sample.SampleOrderService})
 * - Sample HTTP controller ({@link com.raynermendez.spring_boot_mcp_companion.sample.SampleOrderController})
 *
 * MCP endpoints are available at: http://localhost:8080/mcp/
 * Actuator endpoints: http://localhost:8080/actuator/
 *
 * Configuration: application.yml (mcp.server.* properties)
 */
@SpringBootApplication
@EnableMcpCompanion
public class SpringBootMcpCompanionApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringBootMcpCompanionApplication.class, args);
	}

}
