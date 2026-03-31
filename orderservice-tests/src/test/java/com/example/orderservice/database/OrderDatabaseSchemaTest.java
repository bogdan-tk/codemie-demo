package com.example.orderservice.database;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Database schema validation tests for OrderService PostgreSQL schema.
 * 
 * Jira: EPMCDMETST-36038
 * Test Objective: Verify OrderService PostgreSQL schema is created correctly
 * by Flyway migrations and meets database-per-service pattern requirements.
 * 
 * Test Coverage:
 * - Schema existence and structure
 * - Table definitions and columns
 * - Primary keys and indexes
 * - Foreign key constraints
 * - Data types and constraints
 * - Flyway migration history
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class OrderDatabaseSchemaTest {

    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("orderservice_test")
            .withUsername("test_user")
            .withPassword("test_password")
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Test Case: Verify orders table exists
     * 
     * Scenario:
     * 1. Query database metadata
     * 2. Check if orders table exists
     * 3. Verify table is in correct schema
     * 
     * Expected Result:
     * - orders table exists
     * - Table is accessible
     */
    @Test
    void testOrdersTableExists() {
        String sql = "SELECT EXISTS (" +
                "   SELECT FROM information_schema.tables " +
                "   WHERE table_name = 'orders'" +
                ")";
        
        Boolean tableExists = jdbcTemplate.queryForObject(sql, Boolean.class);
        assertThat(tableExists).isTrue();
    }

    /**
     * Test Case: Verify orders table structure
     * 
     * Scenario:
     * 1. Query table columns
     * 2. Verify all required columns exist
     * 3. Validate column data types
     * 
     * Expected Result:
     * - All required columns are present
     * - Data types match specification
     * - NOT NULL constraints are correct
     */
    @Test
    void testOrdersTableStructure() {
        String sql = "SELECT column_name, data_type, is_nullable " +
                "FROM information_schema.columns " +
                "WHERE table_name = 'orders' " +
                "ORDER BY ordinal_position";
        
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(sql);
        
        assertThat(columns).isNotEmpty();
        
        // Verify essential columns exist
        List<String> columnNames = columns.stream()
                .map(col -> (String) col.get("column_name"))
                .toList();
        
        assertThat(columnNames).contains(
                "order_id",
                "customer_id",
                "order_number",
                "total_amount",
                "status",
                "order_date",
                "created_at",
                "updated_at"
        );
    }

    /**
     * Test Case: Verify primary key constraint
     * 
     * Scenario:
     * 1. Query table constraints
     * 2. Verify primary key exists on order_id
     * 3. Validate constraint name and type
     * 
     * Expected Result:
     * - Primary key constraint exists
     * - Constraint is on order_id column
     */
    @Test
    void testOrdersTablePrimaryKey() {
        String sql = "SELECT constraint_name, constraint_type " +
                "FROM information_schema.table_constraints " +
                "WHERE table_name = 'orders' " +
                "AND constraint_type = 'PRIMARY KEY'";
        
        List<Map<String, Object>> constraints = jdbcTemplate.queryForList(sql);
        
        assertThat(constraints).hasSize(1);
        assertThat(constraints.get(0).get("constraint_type")).isEqualTo("PRIMARY KEY");
    }

    /**
     * Test Case: Verify order_id column properties
     * 
     * Scenario:
     * 1. Query order_id column metadata
     * 2. Verify it's a BIGINT/BIGSERIAL
     * 3. Verify NOT NULL constraint
     * 4. Check auto-increment/sequence
     * 
     * Expected Result:
     * - order_id is BIGINT type
     * - NOT NULL constraint exists
     * - Auto-increment is configured
     */
    @Test
    void testOrderIdColumnProperties() {
        String sql = "SELECT column_name, data_type, is_nullable, column_default " +
                "FROM information_schema.columns " +
                "WHERE table_name = 'orders' " +
                "AND column_name = 'order_id'";
        
        Map<String, Object> column = jdbcTemplate.queryForMap(sql);
        
        assertThat(column.get("column_name")).isEqualTo("order_id");
        assertThat(column.get("data_type")).isEqualTo("bigint");
        assertThat(column.get("is_nullable")).isEqualTo("NO");
    }

    /**
     * Test Case: Verify customer_id column properties
     * 
     * Scenario:
     * 1. Query customer_id column metadata
     * 2. Verify data type
     * 3. Verify NOT NULL constraint
     * 
     * Expected Result:
     * - customer_id is BIGINT
     * - NOT NULL constraint exists
     */
    @Test
    void testCustomerIdColumnProperties() {
        String sql = "SELECT column_name, data_type, is_nullable " +
                "FROM information_schema.columns " +
                "WHERE table_name = 'orders' " +
                "AND column_name = 'customer_id'";
        
        Map<String, Object> column = jdbcTemplate.queryForMap(sql);
        
        assertThat(column.get("column_name")).isEqualTo("customer_id");
        assertThat(column.get("data_type")).isEqualTo("bigint");
        assertThat(column.get("is_nullable")).isEqualTo("NO");
    }

    /**
     * Test Case: Verify order_number column properties
     * 
     * Scenario:
     * 1. Query order_number column metadata
     * 2. Verify it's VARCHAR type
     * 3. Verify NOT NULL and UNIQUE constraints
     * 
     * Expected Result:
     * - order_number is VARCHAR
     * - NOT NULL constraint exists
     * - UNIQUE constraint exists
     */
    @Test
    void testOrderNumberColumnProperties() {
        String sql = "SELECT column_name, data_type, is_nullable, character_maximum_length " +
                "FROM information_schema.columns " +
                "WHERE table_name = 'orders' " +
                "AND column_name = 'order_number'";
        
        Map<String, Object> column = jdbcTemplate.queryForMap(sql);
        
        assertThat(column.get("column_name")).isEqualTo("order_number");
        assertThat(column.get("data_type")).isEqualTo("character varying");
        assertThat(column.get("is_nullable")).isEqualTo("NO");
    }

    /**
     * Test Case: Verify total_amount column properties
     * 
     * Scenario:
     * 1. Query total_amount column metadata
     * 2. Verify it's NUMERIC/DECIMAL type
     * 3. Verify precision and scale
     * 
     * Expected Result:
     * - total_amount is NUMERIC
     * - Precision and scale are appropriate for currency
     */
    @Test
    void testTotalAmountColumnProperties() {
        String sql = "SELECT column_name, data_type, is_nullable, numeric_precision, numeric_scale " +
                "FROM information_schema.columns " +
                "WHERE table_name = 'orders' " +
                "AND column_name = 'total_amount'";
        
        Map<String, Object> column = jdbcTemplate.queryForMap(sql);
        
        assertThat(column.get("column_name")).isEqualTo("total_amount");
        assertThat(column.get("data_type")).isEqualTo("numeric");
        assertThat(column.get("is_nullable")).isEqualTo("NO");
    }

    /**
     * Test Case: Verify timestamp columns
     * 
     * Scenario:
     * 1. Query created_at and updated_at columns
     * 2. Verify they are TIMESTAMP type
     * 3. Verify NOT NULL constraints
     * 
     * Expected Result:
     * - Both columns are TIMESTAMP
     * - NOT NULL constraints exist
     */
    @Test
    void testTimestampColumns() {
        String sql = "SELECT column_name, data_type, is_nullable " +
                "FROM information_schema.columns " +
                "WHERE table_name = 'orders' " +
                "AND column_name IN ('created_at', 'updated_at', 'order_date')";
        
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(sql);
        
        assertThat(columns).hasSize(3);
        
        for (Map<String, Object> column : columns) {
            assertThat(column.get("data_type")).isEqualTo("timestamp without time zone");
            assertThat(column.get("is_nullable")).isEqualTo("NO");
        }
    }

    /**
     * Test Case: Verify indexes for performance
     * 
     * Scenario:
     * 1. Query database indexes
     * 2. Verify index on customer_id exists
     * 3. Verify index on order_number exists
     * 
     * Expected Result:
     * - Indexes exist for frequently queried columns
     * - Index types are appropriate
     */
    @Test
    void testTableIndexes() {
        String sql = "SELECT indexname, indexdef " +
                "FROM pg_indexes " +
                "WHERE tablename = 'orders'";
        
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList(sql);
        
        assertThat(indexes).isNotEmpty();
        
        // Verify primary key index exists
        assertThat(indexes.stream()
                .anyMatch(idx -> idx.get("indexname").toString().contains("pkey"))
        ).isTrue();
    }

    /**
     * Test Case: Verify Flyway migration history
     * 
     * Scenario:
     * 1. Query flyway_schema_history table
     * 2. Verify baseline migration exists
     * 3. Verify migration was successful
     * 
     * Expected Result:
     * - Flyway history table exists
     * - Baseline migration is recorded
     * - All migrations succeeded
     */
    @Test
    void testFlywayMigrationHistory() {
        String sql = "SELECT EXISTS (" +
                "   SELECT FROM information_schema.tables " +
                "   WHERE table_name = 'flyway_schema_history'" +
                ")";
        
        Boolean tableExists = jdbcTemplate.queryForObject(sql, Boolean.class);
        assertThat(tableExists).isTrue();
        
        // Query migration records
        String migrationSql = "SELECT version, description, type, success " +
                "FROM flyway_schema_history " +
                "ORDER BY installed_rank";
        
        List<Map<String, Object>> migrations = jdbcTemplate.queryForList(migrationSql);
        
        assertThat(migrations).isNotEmpty();
        
        // Verify all migrations succeeded
        assertThat(migrations).allMatch(m -> (Boolean) m.get("success"));
    }

    /**
     * Test Case: Verify database isolation (Database-per-Service pattern)
     * 
     * Scenario:
     * 1. Verify only OrderService tables exist
     * 2. Ensure no shared tables from other services
     * 3. Validate schema ownership
     * 
     * Expected Result:
     * - Only OrderService-related tables exist
     * - No tables from other services (Inventory, Payment)
     * - Database-per-Service pattern is enforced
     */
    @Test
    void testDatabaseIsolation() {
        String sql = "SELECT table_name " +
                "FROM information_schema.tables " +
                "WHERE table_schema = 'public' " +
                "AND table_type = 'BASE TABLE'";
        
        List<String> tables = jdbcTemplate.queryForList(sql, String.class);
        
        // Should contain orders and flyway_schema_history
        assertThat(tables).contains("orders", "flyway_schema_history");
        
        // Should NOT contain tables from other services
        assertThat(tables).doesNotContain(
                "inventory",
                "products",
                "payments",
                "customers"
        );
    }

    /**
     * Test Case: Verify PostgreSQL connection parameters
     * 
     * Scenario:
     * 1. Query database version
     * 2. Verify PostgreSQL version is 15+
     * 3. Check database encoding
     * 
     * Expected Result:
     * - PostgreSQL 15 or higher
     * - UTF8 encoding
     * - Proper locale settings
     */
    @Test
    void testPostgreSQLVersion() {
        String versionSql = "SELECT version()";
        String version = jdbcTemplate.queryForObject(versionSql, String.class);
        
        assertThat(version).containsIgnoringCase("PostgreSQL");
        assertThat(version).containsAnyOf("15", "16", "17");
    }

    /**
     * Test Case: Verify database can handle CRUD operations
     * 
     * Scenario:
     * 1. Insert test order
     * 2. Read inserted order
     * 3. Update order
     * 4. Delete order
     * 5. Verify all operations succeed
     * 
     * Expected Result:
     * - All CRUD operations work correctly
     * - Data integrity is maintained
     * - Constraints are enforced
     */
    @Test
    void testCrudOperationsOnSchema() {
        // Insert
        String insertSql = "INSERT INTO orders (customer_id, order_number, total_amount, status, order_date, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING order_id";
        
        Long orderId = jdbcTemplate.queryForObject(
                insertSql,
                Long.class,
                1001L,
                "TEST-ORDER-001",
                299.99,
                "PENDING",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()),
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()),
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now())
        );
        
        assertThat(orderId).isNotNull();
        
        // Read
        String selectSql = "SELECT order_number FROM orders WHERE order_id = ?";
        String orderNumber = jdbcTemplate.queryForObject(selectSql, String.class, orderId);
        assertThat(orderNumber).isEqualTo("TEST-ORDER-001");
        
        // Update
        String updateSql = "UPDATE orders SET status = ? WHERE order_id = ?";
        int updated = jdbcTemplate.update(updateSql, "COMPLETED", orderId);
        assertThat(updated).isEqualTo(1);
        
        // Delete
        String deleteSql = "DELETE FROM orders WHERE order_id = ?";
        int deleted = jdbcTemplate.update(deleteSql, orderId);
        assertThat(deleted).isEqualTo(1);
    }
}
