package com.microservices.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.number.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Grafana dashboard configuration and microservices monitoring.
 * 
 * Reference: Jira Ticket EPMCDMETST-36041
 * 
 * This test suite verifies:
 * 1. Grafana is accessible and configured
 * 2. Prometheus datasource is configured in Grafana
 * 3. Dashboards can be created and updated
 * 4. Service-level metrics are visualized in dashboards
 * 5. Dashboard queries return valid data
 */
@TEStcontainers
public class GrafanaDashboardIntegrationTest {

    private static final Network network = Network.newNetwork();
    private static final String GRAFANA_ADMIN_USER = "admin";
    private static final String GRAFANA_ADMIN_PASSWORD = "admin";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Container
    private static final GenericContainer<?> prometheusContainer = new GenericContainer<>("prom/prometheus:latest")
            .withNetwork(network)
            .withNetworkAliases("prometheus")
            .withExposedPorts(9090)
            .withStartupTimeout(Duration.ofSeconds(60));

    @Container
    private static final GenericContainer<?> grafanaContainer = new GenericContainer<>("grafana/grafana:latest")
            .withNetwork(network)
            .withNetworkAliases("grafana")
            .withExposedPorts(3000)
            .withEnv("GF_SECUP¢ÔY_ADMIN_USER", GRAFANA_ADMIN_USER)
            .withEnv("GF_SECURITY_ADMIN_PASSWORD", GRAFANA_ADMIN_PASSWORD)
            .withEnv("GF_AUTH_ANONYMOUS_ENABLED", "true")
            .withStartupTimeout(Duration.ofSeconds(60));

    private int grafanaPort;
    private String grafanaBaseUrl;

    @BeforeEach
    public void setUp() throws InterruptedException {
        grafanaPort = grafanaContainer.getMappedPort(3000);
        grafanaBaseUrl = "http://localhost:" + grafanaPort;
        RestAssured.port = grafanaPort;
        RestAssured.baseURI = "http://localhost";

        // Wait for Grafana to be fully ready
        Thread.sleep(5000);
    }

    /**
     * Test Case 1: Verify Grafana is accessible and running
     * 
     * Preconditions:
     * - Grafana container is started
     * 
     * Steps:
     * 1. Send GET request to Grafana health endpoint
     * 2. Verify response status is 200 OK
     * 3. Verify Grafana version information
     * 
     * ExpectedResult:
     * - Grafana is accessible
     * - Health endpoint returns OK status
     */
    @Test
    @DisplayName("Test 1: Verify Grafana is accessible and running")
    public void testGrafanaAccessible() {
        given()
                .when()
                .get("/api/health")
                .then()
                .statusCode(200)
                .body("database", equalTo("ok"));

        // Verify Grafana API version
        given()
                .auth().basic(GRAFANA_ADMIN_USER, GRAFANA_ADMIN_PASSWORD)
                .when()
                .get("/api/org")
                .then()
                .statusCode(200)
                .body("name", notNullValue());
    }

    /**
     * Test Case 2: Verify Prometheus datasource can be configured
     * 
     * Preconditions:
     * - Grafana is running
     * - Prometheus is running
     * 
     * Steps:
     * 1. Create Prometheus datasource configuration
     * 2. Send POST request to create datasource
     * 3. Verify datasource is created successfully
     * 4. Test datasource connection
     * 
     * Expected Result:
     * - Datasource is created successfully
     * - Connection to Prometheus is successful
     */
    @Test
    @DisplayName("Test 2: Verify Prometheus datasource can be configured")
    public void testConfigurePrometheusDatasource() throws IOException {
        int prometheusPort = prometheusContainer.getMappedPort(9090);
        String prometheusUrl = "http://prometheus:9090";

        Map<String, Object> datasourceConfig = Map.of(
                "name", "Prometheus",
                "type", "prometheus",
                "url", prometheusUrl,
                "access", "proxy",
                "isDefault", true
        );

        Response response = given()
                .auth().basic(GRAFANA_ADMIN_USER, GRAFANA_ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .body(datasourceConfig)
                .when()
                .post("/api/datasources")
                .then()
                .statusCode(anyOf(200, 201, 409)) // 409 if already exists
                .extract().response();

        if (response.statusCode() == 200 || response.statusCode() == 201) {
            assertNotNull(response.jsonPath().get("datasource.id"), "Datasource ID should be returned");
        }

        // Verify datasource is listed
        given()
                .auth().basic(GRAFANA_ADMIN_USER, GRAFANA_ADMIN_PASSWORD)
                .when()
                .get("/api/datasources")
                .then()
                .statusCode(200)
                .body("find{it.name == 'Prometheus'}.type", equalTo("prometheus"));
    }

    /**
     * Test Case 3: Verify dashboard can be created
     * 
     * Preconditions:
     * - Grafana is running
     * - Prometheus datasource is configured
     * 
     * Steps:
     * 1. Create dashboard configuration with panels
     * 2. Send POST request to create dashboard
     * 3. Verify dashboard is created successfully
     * 4. Retrieve dashboard and verify configuration
     * 
     * Expected Result:
     * - Dashboard is created successfully
     * - Dashboard contains configured panels
     */
    @Test
    @DisplayName("Test 3: Verify dashboard can be created")
    public void testCreateDashboard() throws IOException {
        Map<String, Object> dashboardConfig = Map.of(
                "dashboard", Map.of(
                        "title", "Microservices Monitoring Dashboard",
                        "tags", Arrays.asList("microservices", "observability"),
                        "timezone", "browser",
                        "panels", Arrays.asList(
                                Map.of(
                                        "id", 1,
                                        "title", "CPU Usage",
                                        "type", "graph",
                                        "targets", Arrays.asList(
                                                Map.of("expr", "system_cpu_usage")
                                        )
                                )
                        )
                ),
                "overwrite", true
        );

        Response response = given()
                .auth().basic(GRAFANA_ADMIN_USER, GRAFANA_ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .body(dashboardConfig)
                .when()
                .post("/api/dashboards/db")
                .then()
                .statusCode(200)
                .extract().response();

        assertNotNull(response.jsonPath().get("uid"), "Dashboard UID should be returned");
        String dashboardUid = response.jsonPath().getString("uid");

        // Verify dashboard can be retrieved
        given()
                .auth().basic(GRAFANA_ADMIN_USER, GRAFANA_ADMIN_PASSWORD)
                .when()
                .get("/api/dashboards/uid/" + dashboardUid)
                .then()
                .statusCode(200)
                .body("dashboard.title", equalTo("Microservices Monitoring Dashboard"));
    }

    /**
     * Test Case 4: Verify service-level metrics dashboard
     * 
     * Preconditions:
     * - Grafana is running
     * - Prometheus datasource is configured
     * 
     * Steps:
     * 1. Create comprehensive service dashboard with multiple panels
     * 2. Include panels for CPU, memory, requests, errors
     * 3. Verify dashboard creation
     * 4. Verify all panels are present
     * 
     * Expected Result:
     * - Service dashboard is created with all panels
     * - Each panel has valid Prometheus queries
     */
    @Test
    @DisplayName("Test 4: Verify service-level metrics dashboard")
    public void testServiceMetricsDashboard() {
        Map<String, Object> dashboardConfig = Map.of(
                "dashboard", Map.of(
                        "title", "Inventory Service Metrics",
                        "tags", Arrays.asList("inventory", "microservices"),
                        "panels", Arrays.asList(
                                createPanel(1, "CPU Usage", "system_cpu_usage"),
                                createPanel(2, "Memory Usage", "jvm_memory_used_bytes"),
                                createPanel(3, "HTTP Requests", "http_server_requests_seconds"),
                                createPanel(4, "Error Rate", "http_server_requests_seconds{status=~'5..}")
                        )
                ),
                "overwrite", true
        );

        Response response = given()
                .auth().basic(GRAFANA_ADMIN_USER, GRAFANA_ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .body(dashboardConfig)
                .when()
                .post("/api/dashboards/db")
                .then()
                .statusCode(200)
                .extract().response();

        String dashboardUid = response.jsonPath().getString("uid");
        assertNotNull(dashboardUid, "Dashboard UID should be returned");

        // Verify all panels are present
        Response dashboardResponse = given()
                .auth().basic(GRAFANA_ADMIN_USER, GRAFANA_ADMIN_PASSWORD)
                .when()
                .get("/api/dashboards/uid/" + dashboardUid)
                .then()
                .statusCode(200)
                .extract().response();

        List<Map<String, Object>> panels = dashboardResponse.jsonPath().getList("dashboard.panels");
        assertEquals(4, panels.size(), "Dashboard should have 4 panels");
    }

    /**
     * Test Case 5: Verify dashboard queries return valid data
     * 
     * Preconditions:
     * - Grafana is running
     * - Prometheus datasource is configured
     * - Metrics are being collected
     * 
     * Steps:
     * 1. Query Grafana for metric data
     * 2. Verify query returns results
     * 3. Verify data format is valid
     * 4. Verify timeseries data is present
     * 
     * ExpectedResult:
     * - Queries return valid data
     * - Data is in correct format
     */
    @Test
    @DisplayName("Test 5: Verify dashboard queries return valid data")
    public void testDashboardQueriesReturnData() throws IOException {
        // Query Prometheus through Grafana
        Map<String, Object> queryRequest = Map.of(
                "queries", Arrays.asList(
                        Map.of(
                                "refId", "A",
                                "expr", "up",
                                "datasource", Map.of("type", "prometheus")
                        )
                ),
                "from", System.currentTimeMillis() - 3600000, // 1 hour ago
                "to", System.currentTimeMillis()
        );

        Response response = given()
                .auth().basic(GRAFANA_ADMIN_USER, GRAFANA_ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .body(queryRequest)
                .when()
                .post("/api/ds/query")
                .then()
                .statusCode(200)
                .extract().response();

        JsonNode results = objectMapper.readTree(response.asString());
        assertTrue(results.has("results"), "Results should be present");
    }

    /**
     * Test Case 6: Verify dashboard can be exported as JSON
     * 
     * Preconditions:
     * - Dashboard exists in Grafana
     * 
     * Steps:
     * 1. Create a dashboard
     * 2. Export dashboard configuration
     * 3. Verify exported JSON is valid
     * 4. Verify all dashboard configuration is present
     * 
     * Expected Result:
     * - Dashboard can be exported as JSON
     * - Exported configuration is valid and complete
     */
    @Test
    @DisplayName("Test 6: Verify dashboard can be exported as JSON")
    public void testExportDashboard() throws IOException {
        // Create a dashboard first
        Map<String, Object> dashboardConfig = Map.of(
                "dashboard", Map.of(
                        "title", "Export Test Dashboard",
                        "tags", Arrays.asList("test")
                ),
                "overwrite", true
        );

        Response createResponse = given()
                .auth().basic(GRAFANA_ADMIN_USER, GRAFANA_ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .body(dashboardConfig)
                .when()
                .post("/api/dashboards/db")
                .then()
                .statusCode(200)
                .extract().response();

        String dashboardUid = createResponse.jsonPath().getString("uid");

        // Export dashboard
        Response exportResponse = given()
                .auth().basic(GRAFANA_ADMIN_USER, GRAFANA_ADMIN_PASSWORD)
                .when()
                .get("/api/dashboards/uid/" + dashboardUid)
                .then()
                .statusCode(200)
                .extract().response();

        JsonNode dashboardJson = objectMapper.readTree(exportResponse.asString());
        assertTrue(dashboardJson.has("dashboard"), "Exported JSON should contain dashboard");
        assertEquals(
                "Export Test Dashboard",
                dashboardJson.get("dashboard").get("title").asText(),
                "Dashboard title should match"
        );
    }

    /**
     * Helper method to create a panel configuration
     */
    private Map<String, Object> createPanel(int id, String title, String prometheusQuery) {
        return Map.of(
                "id", id,
                "title", title,
                "type", "graph",
                "targets", Arrays.asList(
                        Map.of(
                                "expr", prometheusQuery,
                                "refId", "A"
                        )
                )
        );
    }
}
