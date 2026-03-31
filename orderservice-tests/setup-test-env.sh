#!/bin/bash

################################################################################
# OrderService Test Setup Script
# Jira: EPMCDMETST-36038
# Purpose: Setup test environment for OrderService integration tests
################################################################################

set -e

echo "====================================="
echo "OrderService Test Environment Setup"
echo "====================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}Error: Docker is not running${NC}"
    echo "Please start Docker and try again"
    exit 1
fi

echo -e "${GREEN}✓ Docker is running${NC}"

# Check if docker-compose is available
if ! command -v docker-compose &> /dev/null; then
    echo -e "${YELLOW}Warning: docker-compose not found, trying docker compose${NC}"
    DOCKER_COMPOSE="docker compose"
else
    DOCKER_COMPOSE="docker-compose"
fi

echo -e "${GREEN}✓ Docker Compose is available${NC}"
echo ""

# Stop and remove existing containers
echo "Stopping existing test containers..."
$DOCKER_COMPOSE -f docker-compose-test.yml down -v
echo -e "${GREEN}✓ Existing containers stopped${NC}"
echo ""

# Start PostgreSQL container
echo "Starting PostgreSQL test database..."
$DOCKER_COMPOSE -f docker-compose-test.yml up -d orderservice-postgres
echo -e "${GREEN}✓ PostgreSQL container started${NC}"
echo ""

# Wait for PostgreSQL to be ready
echo "Waiting for PostgreSQL to be ready..."
max_attempts=30
attempt=0

while [ $attempt -lt $max_attempts ]; do
    if docker exec orderservice-postgres-test pg_isready -U test_user -d orderservice_test > /dev/null 2>&1; then
        echo -e "${GREEN}✓ PostgreSQL is ready${NC}"
        break
    fi
    attempt=$((attempt + 1))
    echo -n "."
    sleep 1
done

if [ $attempt -eq $max_attempts ]; then
    echo -e "${RED}Error: PostgreSQL failed to start within timeout${NC}"
    exit 1
fi

echo ""

# Display connection information
echo "====================================="
echo "Test Database Connection Info"
echo "====================================="
echo "Host: localhost"
echo "Port: 5432"
echo "Database: orderservice_test"
echo "Username: test_user"
echo "Password: test_password"
echo "JDBC URL: jdbc:postgresql://localhost:5432/orderservice_test"
echo ""

# Run database health check
echo "Running database health check..."
if docker exec orderservice-postgres-test psql -U test_user -d orderservice_test -c "SELECT version();" > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Database health check passed${NC}"
    
    # Display PostgreSQL version
    PG_VERSION=$(docker exec orderservice-postgres-test psql -U test_user -d orderservice_test -t -c "SELECT version();" | head -n 1)
    echo "PostgreSQL Version: $PG_VERSION"
else
    echo -e "${RED}Error: Database health check failed${NC}"
    exit 1
fi

echo ""
echo "====================================="
echo -e "${GREEN}Test environment setup complete!${NC}"
echo "====================================="
echo ""
echo "You can now run the tests with:"
echo "  ./mvnw test"
echo "  or"
echo "  ./gradlew test"
echo ""
echo "To stop the test environment:"
echo "  $DOCKER_COMPOSE -f docker-compose-test.yml down"
echo ""
