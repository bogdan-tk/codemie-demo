package com.microservices.observability;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DockerComposeContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Prometheus metrics scraping from microservices.
 * 
 * Reference: Jira Ticket EPMCDMETST-36041
 * 
 * This test suite verifies:
 * 1. Microservices expose metrics endpoints
 * 2. Prometheus can scrape metrics from services
 * 3. Metrics are in correct Prometheus format
 * 4. Custom application metrics are available
 * 5. Metrics are updated in real-time
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PrometheusMetricsIntegrationTest {

    private static final Network network = Network.newNetwork();

    @Container
    private static final GenericContainer<?> prometheusContainer = new GenericContainer<>("prom/prometheus:latest")
            .withNetwork(network)
            .withNetworkAliases("prometheus")
            .withExposedPorts(9090)
            .withCommand(
                    "--config.file=/etc/prometheus/prometheus.yml",
                    "--storage.tsdb.path=/prometheus/",
                    "--web.console.libraries=/usr/share/prometheus/console_libraries",
                    "--web.console.templates=/usr/share/prometheus/consoles"
            )
            .withStartupTimeout(Duration.ofSeconds(60));

    @Container
    private static final GenericContainer<?> inventoryServiceContainer = new GenericContainer<>("inventory-service:latest")
            .withNetwork(network)
            .withNetworkAliases("inventory-service")
            .withExposedPorts(8080, 8081)
            .withEnv("SPRING_PROFILES_ACTIVE", "prod")
            .withEnv("MANAGEMENT_METRICS_EXPORT_PROMETHEUS_ENABLED", "true")
            .withStartupTimeout(Duration.ofSeconds(60));

    @LocalServerPort
    private int port;

    @BeforeEach
    public void setUp() {
        RestAssured.port = inventoryServiceContainer.getMappedPort(8080);
        RestAssured.baseURI = "http://localhost";
    }

    /**
     * Test Case 1: Verify that microservice exposes /actuator/prometheus endpoint
     * 
     * Preconditions:
     * - Inventory service is running
     * - Spring Boot Actuator is enabled
     * - Prometheus metrics export is enabled
     * 
     * Steps:
     * 1. Send GET request to /actuator/prometheus
     * 2. Verify response status is 200 OK
     * 3. Verify content type is text/plain
     * 
     * Expected Result:
     * - Endpoint is accessible
     * - Metrics are returned in Prometheus format
     */
    @Test
    @DisplayName("Test 1: Verify microservice exposes Prometheus metrics endpoint")
    public void testMicroserviceExposesMetricsEndpoint() {
        Response response = given()
                .when()
                .get("/actuator/prometheus")
                .then()
                .statusCode(200)
                .contentType(ContentType.TEXT)
                .extract().response();

        String responseBody = response.asString();
        assertNotNull(responseBody, "Response body should not be null");
        assertFalse(responseBody.isEmpty(), "Metrics response should not be empty");
    }

    /**
     * Test Case 2: Verify Prometheus format metrics are returned
     * 
     * Preconditions:
     * - Metrics endpoint is accessible
     * 
     * Steps:
     * 1. Fetch metrics from /actuator/prometheus
     * 2. Verify metrics contain standard JVM metrics
     * 3. Verify metrics contain HTTP request metrics
     * 4. Verify metrics contain system metrics
     * 
     * Expected Result:
     * - All standard metrics are present
     * - Metrics follow Prometheus naming conventions
     */
    @Test
    @DisplayName("Test 2: Verify Prometheus format metrics are returned")
    public void testPrometheusFormatMetrics() {
        String metrics = given()
                .when()
                .get("/actuator/prometheus")
                .then()
                .statusCode(200)
                .extract().asString();

        // Verify JVM metrics
        assertTrue(metrics.contains("jvm_memory_used_bytes"), "JVM memory metrics should be present");
        assertTrue(metrics.contains("jvm_threads_states_threads"), "JVM thread metrics should be present");
        assertTrue(metrics.contains("jvm_gc_memory_allocated_bytes_total"), "JVM GC metrics should be present");

        // Verify HTTP metrics
        assertTrue(metrics.contains("http_server_requests_seconds"), "HTTP request metrics should be present");

        // Verify system metrics
        assertTrue(metrics.contains("system_cpu_usage"), "System CPU metrics should be present");
        assertTrue(metrics.contains("process_uptime_seconds"), "Process uptime metrics should be present");
    }

    /**
     * Test Case 3: Verify custom application metrics are exposed
     * 
     * Preconditions:
     * - Inventory service has custom metrics configured
     * 
     * Steps:
     * 1. Fetch metrics from /actuator/prometheus
     * 2. Verify custom inventory metrics are present
     * 3. Verify business metrics are present
     * 
     * Expected Result:
     * - Custom metrics are exposed
     * - Metrics have appropriate labels
     */
    @Test
    @DisplayName("Test 3: Verify custom application metrics are exposed")
    public void testCustomApplicationMetrics() {
        String metrics = given()
                .when()
                .get("/actuator/prometheus")
                .then()
                .statusCode(200)
                .extract().asString();

        // Verify custom inventory metrics
        assertTrue(
                metrics.contains("inventory") || metrics.contains("application"),
                "Custom application metrics should be present"
        );

        // Verify metric format (metric_name{label="value"} numeric_value)
        assertTrue(
                metrics.matches(".*\\w+\\{[^\\}]*\\}\\s+[0-9]+.*"),
                "Metrics should follow Prometheus format"
        );
    }

    /**
     * Test Case 4: Verify Prometheus can scrape metrics from service
     * 
     * Preconditions:
     * - Prometheus container is running
     * - Inventory service is running
     * - Prometheus is configured to scrape service
     * 
     * Steps:
     * 1. Configure Prometheus to scrape inventory service
     * 2. Wait for scrape interval
     * 3. Query Prometheus API for scraped metrics
     * 4. Verify metrics are available in Prometheus
     * 
     * Expected Result:
     * - Prometheus successfully scrapes metrics
     * - Metrics are stored and queryable
     */
    @Test
    @DisplayName("Test 4: Verify Prometheus can scrape metrics from service")
    public void testPrometheusScrapesMetrics() throws InterruptedException {
        // Wait for Prometheus to scrape metrics
        Thread.sleep(5000);

        int prometheusPort = prometheusContainer.getMappedPort(9090);

        // Query Prometheus API for targets
        RestAssured.port = prometheusPort;
        Response targetsResponse = given()
                .when()
                .get("/api/v1/targets")
                .then()
                .statusCode(200)
                .extract().response();

        assertNotNull(targetsResponse.jsonPath().get("data.activeTargets"), "Active targets should be present");

        // Query for specific metric
        Response queryResponse = given()
                .queryParam("query", "up")
                .when()
                .get("/api/v1/query")
                .then()
                .statusCode(200)
                .extract().response();

        assertEquals("success", queryResponse.jsonPath().getString("status"), "Query should be successful");
        assertNotNull(queryResponse.jsonPath().get("data.result"), "Query results should be present");
    }

    /**
     * Test Case 5: Verify metrics are updated in real-time
     * 
     * Preconditions:
     * - Inventory service is running
     * - Metrics endpoint is accessible
     * 
     * Steps:
     * 1. Fetch initial metrics values
     * 2. Perform actions that trigger metric updates (e.g., HTTP requests)
     * 3. Fetch metrics again
     * 4. Verify metrics have been updated
     * 
     * Expected Result:
     * - Metrics reflect real-time activity
     * - Counters increment correctly
     */
    @Test
    @DisplayName("Test 5: Verify metrics are updated in real-time")
    public void testMetricsRealTimeUpdate() {
        // Get initial metrics
        String initialMetrics = given()
                .when()
                .get("/actuator/prometheus")
                .then()
                .statusCode(200)
                .extract().asString();

        int initialRequestCount = extractRequestCount(initialMetrics);

        // Perform some requests to trigger metric updates
        for (int i = 0; i < 5; i++) {
            given()
                    .when()
                    .get("/api/inventory")
                    .then();
        }

        // Get updated metrics
        String updatedMetrics = given()
                .when()
                .get("/actuator/prometheus")
                .then()
                .statusCode(200)
                .extract().asString();

        int updatedRequestCount = extractRequestCount(updatedMetrics);

        // Verify metrics have been updated
        assertTrue(
                updatedRequestCount > initialRequestCount,
                "Request count metrics should increase after making requests"
        );
    }

    /**
     * Test Case 6: Verify health endpoint is available for monitoring
     * 
     * Preconditions:
     * - Inventory service is running
     * 
     * Steps:
     * 1. Send GET request to /actuator/health
     * 2. Verify response status is 200 OK
     * 3. Verify health status is "UP"
     * 
     * ExpectedResult:
     * - Health endpoint is accessible
     * - Service reports healthy status
     */
    @Test
    @DisplayName("Test 6: Verify health endpoint is available for monitoring")
    public void testHealthEndpointAvailable() {
        given()
                .when()
                .get("/actuator/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    /**
     * Helper method to extract request count from metrics
     */
    private int extractRequestCount(String metrics) {
        // Extract http_server_requests_total metric
        String[] lines = metrics.split("\\n");
        for (String line : lines) {
            if (line.startsWith("http_server_requests") && !line.startsWith("#")) {
                String[] parts = line.split("\\s+");
                if (parts.length > 1) {
                    try {
                        return (int) Double.parseDouble(parts[parts.length - 1]);
                    } catch (NumberFormatException e) {
                        // Continue to next line
                    }
                }
            }
        }
        return 0;
    }
}
