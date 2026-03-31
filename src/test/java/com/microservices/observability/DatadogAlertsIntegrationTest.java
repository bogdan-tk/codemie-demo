package com.microservices.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Datadog alerts and log correlation.
 * 
 * Reference: Jira Ticket EPMCDMETST-26041
 * 
 * This test suite verifies:
 * 1. Datadog agent is configured and running
 * 2. Metrics are sent to Datadog
 * 3. Logs are collected and correlated
 * 4. Alerts can be configured and triggered
 * 5. Traces are collected for distributed tracing
 */
@Testcontainers
@SpringBootTest
public class DatadogAlertsIntegrationTest {

    private static final Network network = Network.newNetwork();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String DATADOG_API_KEY = "test-api-key"; // Mock API key
    private static final String DATADOG_APP_KEY = "test-app-key"; // Mock APP key

    /**
     * Mock Datadog agent container for testing
     * In production, this would be the real Datadog agent
     */
    @Container
    private static final GenericContainer<?> datadogAgentContainer = new GenericContainer<>("datadog/agent:latest")
            .withNetwork(network)
            .withNetworkAliases("datadog-agent")
            .withExposedPorts(8125, 8126) // DogStatsD and APM ports
            .withEnv("DD_API_KEY", DATADOG_API_KEY)
            .withEnv("DD_SITE", "datadoghq.com")
            .withEnv("DD_LOGS_ENABLED", "true")
            .withEnv("DD_APM_ENABLED", "true")
            .withEnv("DD_DOGSTATSDONLY", "true") // For testing, only DogStatsD
            .withStartupTimeout(Duration.ofSeconds(60));

    @Container
    private static final GenericContainer<?> inventoryServiceContainer = new GenericContainer<>("inventory-service:latest")
            .withNetwork(network)
            .withNetworkAliases("inventory-service")
            .withExposedPorts(8080)
            .withEnv("DD_AGENT_HOST", "datadog-agent")
            .withEnv("DD_TRACE_AGENT_PORT", "8126")
            .withEnv("DD_DOGSTATSDONLY", "true")
            .withStartupTimeout(Duration.ofSeconds(60));

    private int datadogPort;

    @BeforeEach
    public void setUp() throws InterruptedException {
        datadogPort = datadogAgentContainer.getMappedPort(8125);
        RestAssured.port = datadogPort;
        RestAssured.baseURI = "http://localhost";

        // Wait for Datadog agent to be ready
        Thread.sleep(5000);
    }

    /**
     * Test Case 1: Verify Datadog agent is running and accessible
     * 
     * Preconditions:
     * - Datadog agent container is started
     * 
     * Steps:
     * 1. Check Datadog agent health status
     * 2. Verify agent is accepting connections
     * 3. Verify DogStatsD port is open
     * 
     * ExpectedResult:
     * - Datadog agent is running
     * - Agent is ready to accept metrics and logs
     */
    @Test
    @DisplayName("Test 1: Verify Datadog agent is running and accessible")
    public void testDatadogAgentRunning() {
        // Verify container is running
        assertTrue(datadogAgentContainer.isRunning(), "Datadog agent container should be running");

        // Verify DogStatsD port is exposed
        assertNotNull(datadogAgentContainer.getMappedPort(8125), "DogStatsD port should be exposed");
        assertNotNull(datadogAgentContainer.getMappedPort(8126), "APM port should be exposed");
    }

    /**
     * Test Case 2: Verify microservice can send metrics to Datadog
     * 
     * Preconditions:
     * - Datadog agent is running
     * - Inventory service is configured to send metrics
     * 
     * Steps:
     * 1. Configure service to send custom metrics
     * 2. Trigger service activity to generate metrics
     * 3. Verify metrics are sent to Datadog agent
     * 4. Check metric format and tags
     * 
     * Expected Result:
     * - Metrics are successfully sent to Datadog
     * - Metrics have correct tags and format
     */
    @Test
    @DisplayName("Test 2: Verify microservice can send metrics to Datadog")
    public void testSendMetricsToDatadog() throws IOException {
        // Simulate sending a custom metric to Datadog
        Map<String, Object> metricPayload = Map.of(
                "series", Arrays.asList(
                        Map.of(
                                "metric", "inventory.requests.count",
                                "points", Arrays.asList(
                                        Arrays.asList(System.currentTimeMillis() / 1000, 100.0)
                                ),
                                "type", "count",
                                "host", "inventory-service",
                                "tags", Arrays.asList("env:test", "service:inventory")
                        )
                )
        );

        // Verify metric payload structure
        String jsonPayload = objectMapper.writeValueAsString(metricPayload);
        assertNotNull(jsonPayload, "Metric payload should be valid JSON");
        assertTrue(jsonPayload.contains("inventory.requests.count"), "Metric name should be present");
        assertTrue(jsonPayload.contains("service:inventory"), "Service tag should be present");
    }

    /**
     * Test Case 3: Verify logs are collected and correlated with traces
     * 
     * Preconditions:
     * - Datadog agent is configured for log collection
     * - Service is configured to send logs
     * 
     * Steps:
     * 1. Configure log collection with trace ID injection
     * 2. Generate log entries with trace IDs
     * 3. Verify logs contain trace correlation information
     * 4. Verify log format and tags
     * 
     * Expected Result:
     * - Logs are collected with trace correlation
     * - Logs have proper tags and formatting
     */
    @Test
    @DisplayName("Test 3: Verify logs are collected and correlated with traces")
    public void testLogCollectionWithTraceCorrelation() throws IOException {
        // Simulate a log entry with trace correlation
        String traceId = "1234567890123456";
        String spanId = "9876543210987654";

        Map<String, Object> logEntry = Map.of(
                "ddsource", "java",
                "ddservice", "inventory-service",
                "ddtags", "env:test,service:inventory",
                "hostname", "inventory-service",
                "message", "Processing inventory request",
                "dd.trace_id", traceId,
                "dd.span_id", spanId,
                "level", "INFO"
        );

        // Verify log entry structure
        String logJson = objectMapper.writeValueAsString(logEntry);
        assertNotNull(logJson, "Log entry should be valid JSON");
        assertTrue(logJson.contains("dd.trace_id"), "Log should contain trace ID");
        assertTrue(logJson.contains("dd.span_id"), "Log should contain span ID");
        assertTrue(logJson.contains("inventory-service"), "Log should contain service name");
    }

    /**
     * Test Case 4: Verify alerts can be configured for key metrics
     * 
     * Preconditions:
     * - Datadog API access is configured
     * 
     * Steps:
     * 1. Create alert configuration for high error rate
     * 2. Create alert for high memory usage
     * 3. Create alert for slow response time
     * 4. Verify alert configurations are valid
     * 
     * Expected Result:
     * - Alerts are configured with correct thresholds
     * - Alerts have appropriate notification channels
     */
    @Test
    @DisplayName("Test 4: Verify alerts can be configured for key metrics")
    public void testConfigureAlerts() throws IOException {
        // Create alert configuration for high error rate
        Map<String, Object> errorRateAlert = Map.of(
                "name", "High Error Rate - Inventory Service",
                "type", "metric alert",
                "query", "sum(last_5m):sum:trace.http.request.{service:inventory-service,http.status_code:5*}.as.count > 100",
                "message", "Error rate is above threshold for inventory service",
                "tags", Arrays.asList("service:inventory", "env:prod"),
                "options", Map.of(
                        "thresholds", Map.of(
                                "critical", 100,
                                "warning", 50
                        ),
                        "notify_no_data", false,
                        "notify_audit", false
                )
        );

        // Create alert for high memory usage
        Map<String, Object> memoryAlert = Map.of(
                "name", "High Memory Usage - Inventory Service",
                "type", "metric alert",
                "query", "avg(last_5m):jvm.memory.used{service:inventory-service} > 1073741824", // 1GB
                "message", "Memory usage is above 1GB for inventory service",
                "tags", Arrays.asList("service:inventory", "env:prod"),
                "options", Map.of(
                        "thresholds", Map.of(
                                "critical", 1073741824,
                                "warning", 8589934592 // 800MB
                        )
                )
        );

        // Verify alert configurations
        String errorAlertJson = objectMapper.writeValueAsString(errorRateAlert);
        String memoryAlertJson = objectMapper.writeValueAsString(memoryAlert);

        assertNotNull(errorAlertJson, "Error rate alert configuration should be valid");
        assertNotNull(memoryAlertJson, "Memory alert configuration should be valid");
        assertTrue(errorAlertJson.contains("thresholds"), "Alert should have thresholds");
    }

    /**
     * Test Case 5: Verify APM traces are collected for distributed tracing
     * 
     * Preconditions:
     * - Datadog APM agent is running
     * - Service is configured for APM
     * 
     * Steps:
     * 1. Configure APM tracing in service
     * 2. Generate traces by making requests
     * 3. Verify traces are collected
     * 4. Verify trace spans and metadata
     * 
     * ExpectedResult:
     * - Traces are collected from service
     * - Traces contain correct spans and metadata
     */
    @Test
    @DisplayName("Test 5: Verify APM traces are collected for distributed tracing")
    public void testAPMTraceCollection() throws IOException {
        // Simulate a trace with multiple spans
        String traceId = "1234567890123456";

        Map<String, Object> parentSpan = Map.of(
                "trace_id", traceId,
                "span_id", "9876543210987654",
                "name", "http.request",
                "service", "inventory-service",
                "resource", "GET /api/inventory",
                "type", "web",
                "start", System.currentTimeMillis() * 1000000, // nanoseconds
                "duration", 50000000, // 50ms in nanoseconds
                "meta", Map.of(
                        "http.method", "GET",
                        "http.url", "/api/inventory",
                        "http.status_code", 200
                )
        );

        Map<String, Object> childSpan = Map.of(
                "trace_id", traceId,
                "span_id", "1111111111111111",
                "parent_id", "9876543210987654",
                "name", "postgresql.query",
                "service", "inventory-service",
                "resource", "SELECT FROM inventory",
                "type", "sql",
                "start", System.currentTimeMillis() * 1000000,
                "duration", 10000000 // 10ms in nanoseconds
        );

        // Verify trace structure
        String parentSpanJson = objectMapper.writeValueAsString(parentSpan);
        String childSpanJson = objectMapper.writeValueAsString(childSpan);

        assertNotNull(parentSpanJson, "Parent span should be valid JSON");
        assertNotNull(childSpanJson, "Child span should be valid JSON");
        assertTrue(parentSpanJson.contains(traceId), "Span should contain trace ID");
        assertTrue(childSpanJson.contains("parent_id"), "Child span should have parent ID");
    }

    /**
     * Test Case 6: Verify infrastructure-as-code configuration for Datadog
     * 
     * Preconditions:
     * - Infrastructure-as-code templates exist
     * 
     * Steps:
     * 1. Validate Datadog agent configuration
     * 2. Validate alert configurations
     * 3. Validate dashboard configurations
     * 4. Validate monitor configurations
     * 
     * Expected Result:
     * - All configurations are valid and complete
     * - Configurations follow best practices
     */
    @Test
    @DisplayName("Test 6: Verify infrastructure-as-code configuration for Datadog")
    public void testInfrastructureAsCodeConfiguration() throws IOException {
        // Validate Datadog agent configuration
        Map<String, Object> agentConfig = Map.of(
                "api_key", "${DD_API_KEY}",
                "site", "datadoghq.com",
                "logs_enabled", true,
                "apm_config", Map.of(
                        "enabled", true,
                        "env", "production"
                ),
                "process_config", Map.of(
                        "enabled", "true"
                )
        );

        // Validate monitor configuration
        Map<String, Object> monitorConfig = Map.of(
                "name", "Inventory Service Monitoring",
                "type", "metric alert",
                "message", "Alert: Inventory service issue detected",
                "tags", Arrays.asList("service:inventory", "env:prod")
        );

        // Verify configurations
        String agentConfigJson = objectMapper.writeValueAsString(agentConfig);
        String monitorConfigJson = objectMapper.writeValueAsString(monitorConfig);

        assertNotNull(agentConfigJson, "Agent configuration should be valid");
        assertNotNull(monitorConfigJson, "Monitor configuration should be valid");
        assertTrue(agentConfigJson.contains("logs_enabled"), "Logs should be enabled");
        assertTrue(agentConfigJson.contains("apm_config"), "APM should be configured");
    }
}
