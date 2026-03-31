#!/bin/bash

###############################################################################
# Test Environment Setup Script
# Related to: EPMCDMETST-36024 - Implement AuthService (JWT issuance & validation)
#
# This script sets up the test environment for AuthService integration tests:
# - Configures test database
# - Sets up test users and roles
# - Generates test JWT secrets
# - Validates environment configuration
#
# Author: Test Automation Team
###############################################################################

set -e

echo "========================================="
echo "AuthService Test Environment Setup"
echo "========================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
TEST_DB_NAME="auth_service_test"
TEST_DB_USER="test_user"
TEST_DB_PASSWORD="test_password"
TEST_JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')

echo -e "${YELLOW}Step 1: Checking prerequisites...${NC}"

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo -e "${RED}Error: Java is not installed${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Java found: $(java -version 2>&1 | head -n 1)${NC}"

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}Error: Maven is not installed${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Maven found: $(mvn -version | head -n 1)${NC}"

# Check if Docker is installed (for Testcontainers)
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Error: Docker is not installed${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Docker found: $(docker --version)${NC}"

echo -e "\n${YELLOW}Step 2: Setting up test configuration...${NC}"

# Create test properties file
cat > src/test/resources/application-test.properties <<EOF
# Test Database Configuration
spring.datasource.url=jdbc:h2:mem:${TEST_DB_NAME}
spring.datasource.username=${TEST_DB_USER}
spring.datasource.password=${TEST_DB_PASSWORD}
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect

# JPA Configuration
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

# JWT Configuration
jwt.secret=${TEST_JWT_SECRET}
jwt.issuer=auth-service-test
jwt.expiration-ms=3600000

# Logging
logging.level.com.codemie.demo.auth=DEBUG
logging.level.org.springframework.security=DEBUG

# Test-specific settings
spring.test.mockmvc.print=true
EOF

echo -e "${GREEN}✓ Test configuration created${NC}"

echo -e "\n${YELLOW}Step 3: Creating test data SQL scripts...${NC}"

# Create test data initialization script
cat > src/test/resources/test-data.sql <<EOF
-- Test data for AuthService integration tests
-- Related to: EPMCDMETST-36024

-- Insert test roles
INSERT INTO roles (id, name) VALUES (1, 'ROLE_USER');
INSERT INTO roles (id, name) VALUES (2, 'ROLE_ADMIN');
INSERT INTO roles (id, name) VALUES (3, 'ROLE_SERVICE');

-- Insert test users
-- Password: testPassword123 (BCrypt encoded)
INSERT INTO users (id, username, email, password, enabled) 
VALUES (1, 'testuser', 'testuser@example.com', '\$2a\$10\$N9qo8uLOickgx2ZMRZoMye1J8N/5RQBZQ7xYZVZVZVZVZVZVZVZ', true);

INSERT INTO users (id, username, email, password, enabled) 
VALUES (2, 'adminuser', 'admin@example.com', '\$2a\$10\$N9qo8uLOickgx2ZMRZoMye1J8N/5RQBZQ7xYZVZVZVZVZVZVZVZ', true);

INSERT INTO users (id, username, email, password, enabled) 
VALUES (3, 'disableduser', 'disabled@example.com', '\$2a\$10\$N9qo8uLOickgx2ZMRZoMye1J8N/5RQBZQ7xYZVZVZVZVZVZVZVZ', false);

-- Assign roles to users
INSERT INTO user_roles (user_id, role_id) VALUES (1, 1); -- testuser has ROLE_USER
INSERT INTO user_roles (user_id, role_id) VALUES (2, 1); -- adminuser has ROLE_USER
INSERT INTO user_roles (user_id, role_id) VALUES (2, 2); -- adminuser has ROLE_ADMIN
EOF

echo -e "${GREEN}✓ Test data scripts created${NC}"

echo -e "\n${YELLOW}Step 4: Validating Maven dependencies...${NC}"

# Check if pom.xml exists
if [ ! -f "pom.xml" ]; then
    echo -e "${RED}Error: pom.xml not found${NC}"
    exit 1
fi

# Validate required dependencies
REQUIRED_DEPS=("spring-boot-starter-test" "spring-security-test" "jjwt-api" "h2" "testcontainers")
for dep in "${REQUIRED_DEPS[@]}"; do
    if grep -q "$dep" pom.xml; then
        echo -e "${GREEN}✓ Dependency found: $dep${NC}"
    else
        echo -e "${YELLOW}⚠ Warning: Dependency might be missing: $dep${NC}"
    fi
done

echo -e "\n${YELLOW}Step 5: Building test environment...${NC}"

# Clean and compile
mvn clean compile test-compile -DskipTests

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Build successful${NC}"
else
    echo -e "${RED}✗ Build failed${NC}"
    exit 1
fi

echo -e "\n${GREEN}=========================================${NC}"
echo -e "${GREEN}Test environment setup completed!${NC}"
echo -e "${GREEN}=========================================${NC}"
echo ""
echo -e "Test Database: ${TEST_DB_NAME}"
echo -e "JWT Secret: ${TEST_JWT_SECRET:0:20}..."
echo ""
echo -e "To run tests, execute:"
echo -e "  ${YELLOW}mvn test${NC}"
echo -e "  ${YELLOW}mvn test -Dtest=AuthServiceJwtIssuanceTest${NC}"
echo -e "  ${YELLOW}mvn test -Dtest=AuthServiceIntegrationTest${NC}"
echo ""
echo -e "To run with coverage:"
echo -e "  ${YELLOW}mvn clean test jacoco:report${NC}"
echo ""

exit 0
