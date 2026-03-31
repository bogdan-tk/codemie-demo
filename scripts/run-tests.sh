#!/bin/bash

###############################################################################
# Test Execution Script
# Related to: EPMCDMETST-36024 - Implement AuthService (JWT issuance & validation)
#
# This script executes the complete test suite for AuthService:
# - Runs unit tests
# - Runs integration tests
# - Generates coverage reports
# - Validates test results
#
# Author: Test Automation Team
###############################################################################

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}AuthService Test Suite Execution${NC}"
echo -e "${BLUE}=========================================${NC}"
echo ""

# Parse command line arguments
TEST_SUITE="all"
COVERAGE_REPORT=false
VERBOSE=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --suite)
            TEST_SUITE="$2"
            shift 2
            ;;
        --coverage)
            COVERAGE_REPORT=true
            shift
            ;;
        --verbose)
            VERBOSE=true
            shift
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            exit 1
            ;;
    esac
done

echo -e "${YELLOW}Configuration:${NC}"
echo -e "  Test Suite: ${TEST_SUITE}"
echo -e "  Coverage Report: ${COVERAGE_REPORT}"
echo -e "  Verbose: ${VERBOSE}"
echo ""

# Function to run specific test suite
run_test_suite() {
    local suite_name=$1
    local test_class=$2
    
    echo -e "\n${YELLOW}Running ${suite_name}...${NC}"
    
    if [ "$VERBOSE" = true ]; then
        mvn test -Dtest="${test_class}" -X
    else
        mvn test -Dtest="${test_class}"
    fi
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ ${suite_name} passed${NC}"
        return 0
    else
        echo -e "${RED}✗ ${suite_name} failed${NC}"
        return 1
    fi
}

# Track test results
FAILED_SUITES=()

# Run tests based on suite selection
case $TEST_SUITE in
    "all")
        echo -e "${BLUE}Running all test suites...${NC}"
        run_test_suite "JWT Issuance Tests" "AuthServiceJwtIssuanceTest" || FAILED_SUITES+=("JWT Issuance")
        run_test_suite "JWT Validation Tests" "AuthServiceJwtValidationTest" || FAILED_SUITES+=("JWT Validation")
        run_test_suite "Integration Tests" "AuthServiceIntegrationTest" || FAILED_SUITES+=("Integration")
        run_test_suite "API Gateway Filter Tests" "ApiGatewayAuthFilterTest" || FAILED_SUITES+=("API Gateway")
        ;;
    "unit")
        echo -e "${BLUE}Running unit tests...${NC}"
        run_test_suite "JWT Issuance Tests" "AuthServiceJwtIssuanceTest" || FAILED_SUITES+=("JWT Issuance")
        run_test_suite "JWT Validation Tests" "AuthServiceJwtValidationTest" || FAILED_SUITES+=("JWT Validation")
        ;;
    "integration")
        echo -e "${BLUE}Running integration tests...${NC}"
        run_test_suite "Integration Tests" "AuthServiceIntegrationTest" || FAILED_SUITES+=("Integration")
        run_test_suite "API Gateway Filter Tests" "ApiGatewayAuthFilterTest" || FAILED_SUITES+=("API Gateway")
        ;;
    "issuance")
        run_test_suite "JWT Issuance Tests" "AuthServiceJwtIssuanceTest" || FAILED_SUITES+=("JWT Issuance")
        ;;
    "validation")
        run_test_suite "JWT Validation Tests" "AuthServiceJwtValidationTest" || FAILED_SUITES+=("JWT Validation")
        ;;
    "gateway")
        run_test_suite "API Gateway Filter Tests" "ApiGatewayAuthFilterTest" || FAILED_SUITES+=("API Gateway")
        ;;
    *)
        echo -e "${RED}Unknown test suite: ${TEST_SUITE}${NC}"
        echo -e "Available suites: all, unit, integration, issuance, validation, gateway"
        exit 1
        ;;
esac

# Generate coverage report if requested
if [ "$COVERAGE_REPORT" = true ]; then
    echo -e "\n${YELLOW}Generating coverage report...${NC}"
    mvn jacoco:report
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Coverage report generated${NC}"
        echo -e "  Report location: target/site/jacoco/index.html"
    else
        echo -e "${RED}✗ Coverage report generation failed${NC}"
    fi
fi

# Print summary
echo -e "\n${BLUE}=========================================${NC}"
echo -e "${BLUE}Test Execution Summary${NC}"
echo -e "${BLUE}=========================================${NC}"

if [ ${#FAILED_SUITES[@]} -eq 0 ]; then
    echo -e "${GREEN}All test suites passed! ✓${NC}"
    exit 0
else
    echo -e "${RED}Failed test suites:${NC}"
    for suite in "${FAILED_SUITES[@]}"; do
        echo -e "  ${RED}✗ ${suite}${NC}"
    done
    exit 1
fi
