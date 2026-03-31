#!/bin/bash

################################################################################
# OrderService Test Cleanup Script
# Jira: EPMCDMETST-36038
# Purpose: Clean up test environment and resources
################################################################################

set -e

echo "====================================="
echo "OrderService Test Environment Cleanup"
echo "====================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if docker-compose is available
if ! command -v docker-compose &> /dev/null; then
    echo -e "${YELLOW}Warning: docker-compose not found, trying docker compose${NC}"
    DOCKER_COMPOSE="docker compose"
else
    DOCKER_COMPOSE="docker-compose"
fi

# Stop and remove containers
echo "Stopping test containers..."
if $DOCKER_COMPOSE -f docker-compose-test.yml down -v; then
    echo -e "${GREEN}✓ Test containers stopped and removed${NC}"
else
    echo -e "${YELLOW}Warning: Some containers may not have been running${NC}"
fi

echo ""

# Remove test volumes
echo "Removing test volumes..."
if docker volume ls | grep -q "orderservice-test-data"; then
    docker volume rm orderservice-test-data 2>/dev/null || echo -e "${YELLOW}Volume may already be removed${NC}"
    echo -e "${GREEN}✓ Test volumes removed${NC}"
else
    echo -e "${YELLOW}No test volumes found${NC}"
fi

echo ""

# Remove test network
echo "Removing test network..."
if docker network ls | grep -q "orderservice-test-network"; then
    docker network rm orderservice-test-network 2>/dev/null || echo -e "${YELLOW}Network may already be removed${NC}"
    echo -e "${GREEN}✓ Test network removed${NC}"
else
    echo -e "${YELLOW}No test network found${NC}"
fi

echo ""

# Clean Maven/Gradle build artifacts (optional)
read -p "Do you want to clean build artifacts? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "Cleaning build artifacts..."
    
    if [ -f "mvnw" ]; then
        ./mvnw clean
        echo -e "${GREEN}✓ Maven artifacts cleaned${NC}"
    elif [ -f "gradlew" ]; then
        ./gradlew clean
        echo -e "${GREEN}✓ Gradle artifacts cleaned${NC}"
    else
        echo -e "${YELLOW}No build tool found (mvnw or gradlew)${NC}"
    fi
fi

echo ""
echo "====================================="
echo -e "${GREEN}Test environment cleanup complete!${NC}"
echo "====================================="
echo ""
