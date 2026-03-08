#!/bin/bash

# AfroCow API Quick Test Script
# This script tests the basic authentication and customer journey flow

set -e  # Exit on error

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Base URL
BASE_URL="http://localhost:8080"

# Test data
CUSTOMER_EMAIL="testcustomer_$(date +%s)@example.com"
CUSTOMER_PASSWORD="SecurePass123!"
VENDOR_EMAIL="testvendor_$(date +%s)@example.com"
VENDOR_PASSWORD="VendorPass123!"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}AfroCow API Quick Test${NC}"
echo -e "${BLUE}========================================${NC}\n"

# Function to print step
print_step() {
    echo -e "\n${GREEN}► $1${NC}"
}

# Function to print error
print_error() {
    echo -e "${RED}✗ $1${NC}"
}

# Function to print success
print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

# Test 1: Register Customer
print_step "Test 1: Register Customer"
CUSTOMER_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/register/customer" \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"$CUSTOMER_EMAIL\",
    \"username\": \"customer$(date +%s)\",
    \"password\": \"$CUSTOMER_PASSWORD\",
    \"confirmPassword\": \"$CUSTOMER_PASSWORD\",
    \"firstName\": \"Test\",
    \"lastName\": \"Customer\",
    \"phone\": \"+1234567890\",
    \"acceptTerms\": true,
    \"defaultDeliveryInstructions\": \"Leave at door\",
    \"address\": {
      \"addressLine\": \"123 Test Street\",
      \"city\": \"Toronto\",
      \"province\": \"ONTARIO\",
      \"postalCode\": \"M5H 2N2\",
      \"country\": \"Canada\",
      \"defaultAddress\": true
    }
  }")

if echo "$CUSTOMER_RESPONSE" | grep -q "Registration successful"; then
    print_success "Customer registered successfully"
    CUSTOMER_PUBLIC_ID=$(echo "$CUSTOMER_RESPONSE" | grep -o '"publicUserId":"[^"]*' | cut -d'"' -f4)
    echo "  Email: $CUSTOMER_EMAIL"
    echo "  Public ID: $CUSTOMER_PUBLIC_ID"
else
    print_error "Customer registration failed"
    echo "$CUSTOMER_RESPONSE"
    exit 1
fi

# Test 2: Try to login without email verification (should fail)
print_step "Test 2: Try Login Without Email Verification (Should Fail)"
LOGIN_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{
    \"identifier\": \"$CUSTOMER_EMAIL\",
    \"password\": \"$CUSTOMER_PASSWORD\"
  }")

HTTP_CODE=$(echo "$LOGIN_RESPONSE" | tail -n1)
if [ "$HTTP_CODE" = "403" ]; then
    print_success "Login correctly blocked for unverified email (HTTP 403)"
else
    print_error "Expected HTTP 403, got HTTP $HTTP_CODE"
fi

# Test 3: Register Vendor
print_step "Test 3: Register Vendor"
VENDOR_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/register/vendor" \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"$VENDOR_EMAIL\",
    \"username\": \"vendor$(date +%s)\",
    \"password\": \"$VENDOR_PASSWORD\",
    \"confirmPassword\": \"$VENDOR_PASSWORD\",
    \"firstName\": \"Test\",
    \"lastName\": \"Vendor\",
    \"phone\": \"+1234567891\",
    \"acceptTerms\": true,
    \"restaurantName\": \"Test Restaurant $(date +%s)\",
    \"description\": \"Test restaurant description\",
    \"cuisineType\": \"African\",
    \"deliveryFee\": 5.99,
    \"minimumOrderAmount\": 15.00,
    \"estimatedDeliveryMinutes\": 45,
    \"maxDeliveryDistanceKm\": 10,
    \"openingTime\": \"09:00:00\",
    \"closingTime\": \"22:00:00\",
    \"isOpenToday\": true,
    \"address\": {
      \"addressLine\": \"456 Restaurant Ave\",
      \"city\": \"Toronto\",
      \"province\": \"ONTARIO\",
      \"postalCode\": \"M4B 1B3\",
      \"country\": \"Canada\",
      \"defaultAddress\": true
    }
  }")

if echo "$VENDOR_RESPONSE" | grep -q "Registration successful"; then
    print_success "Vendor registered successfully"
    VENDOR_PUBLIC_ID=$(echo "$VENDOR_RESPONSE" | grep -o '"publicUserId":"[^"]*' | cut -d'"' -f4)
    echo "  Email: $VENDOR_EMAIL"
    echo "  Public ID: $VENDOR_PUBLIC_ID"
else
    print_error "Vendor registration failed"
    echo "$VENDOR_RESPONSE"
    exit 1
fi

# Test 4: Get all products (public endpoint)
print_step "Test 4: Get All Products (Public)"
PRODUCTS_RESPONSE=$(curl -s -w "\n%{http_code}" "$BASE_URL/products")
HTTP_CODE=$(echo "$PRODUCTS_RESPONSE" | tail -n1)
if [ "$HTTP_CODE" = "200" ]; then
    print_success "Products retrieved successfully"
else
    print_error "Failed to get products (HTTP $HTTP_CODE)"
fi

# Test 5: Get all categories (public endpoint)
print_step "Test 5: Get All Categories (Public)"
CATEGORIES_RESPONSE=$(curl -s -w "\n%{http_code}" "$BASE_URL/categories")
HTTP_CODE=$(echo "$CATEGORIES_RESPONSE" | tail -n1)
if [ "$HTTP_CODE" = "200" ]; then
    print_success "Categories retrieved successfully"
else
    print_error "Failed to get categories (HTTP $HTTP_CODE)"
fi

# Test 6: Universal search (public endpoint)
print_step "Test 6: Universal Search (Public)"
SEARCH_RESPONSE=$(curl -s -w "\n%{http_code}" "$BASE_URL/api/search?query=test")
HTTP_CODE=$(echo "$SEARCH_RESPONSE" | tail -n1)
if [ "$HTTP_CODE" = "200" ]; then
    print_success "Search executed successfully"
else
    print_error "Search failed (HTTP $HTTP_CODE)"
fi

# Test 7: Test password validation
print_step "Test 7: Test Password Validation (Should Fail)"
WEAK_PASSWORD_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/auth/register/customer" \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"weak_$(date +%s)@example.com\",
    \"username\": \"weakuser$(date +%s)\",
    \"password\": \"weak\",
    \"confirmPassword\": \"weak\",
    \"firstName\": \"Test\",
    \"acceptTerms\": true,
    \"address\": {
      \"addressLine\": \"123 Test\",
      \"city\": \"Toronto\",
      \"province\": \"ONTARIO\",
      \"postalCode\": \"M5H 2N2\"
    }
  }")

HTTP_CODE=$(echo "$WEAK_PASSWORD_RESPONSE" | tail -n1)
if [ "$HTTP_CODE" = "400" ]; then
    print_success "Weak password correctly rejected (HTTP 400)"
else
    print_error "Expected HTTP 400 for weak password, got HTTP $HTTP_CODE"
fi

# Test 8: Test rate limiting (registration)
print_step "Test 8: Test Rate Limiting (6 Rapid Registrations)"
echo "  Attempting 6 rapid registrations from same IP..."
RATE_LIMIT_HIT=false
for i in {1..6}; do
    RATE_TEST_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/auth/register/customer" \
      -H "Content-Type: application/json" \
      -d "{
        \"email\": \"ratelimit_${i}_$(date +%s)@example.com\",
        \"username\": \"ratelimit${i}$(date +%s)\",
        \"password\": \"$CUSTOMER_PASSWORD\",
        \"confirmPassword\": \"$CUSTOMER_PASSWORD\",
        \"firstName\": \"Rate\",
        \"lastName\": \"Test\",
        \"acceptTerms\": true,
        \"address\": {
          \"addressLine\": \"123 Test\",
          \"city\": \"Toronto\",
          \"province\": \"ONTARIO\",
          \"postalCode\": \"M5H 2N2\"
        }
      }")

    HTTP_CODE=$(echo "$RATE_TEST_RESPONSE" | tail -n1)
    if [ "$HTTP_CODE" = "429" ]; then
        RATE_LIMIT_HIT=true
        print_success "Rate limit hit on attempt $i (HTTP 429)"
        break
    fi
done

if [ "$RATE_LIMIT_HIT" = false ]; then
    echo "  ⚠ Rate limit not hit after 6 attempts (may need adjustment)"
fi

# Summary
echo -e "\n${BLUE}========================================${NC}"
echo -e "${BLUE}Test Summary${NC}"
echo -e "${BLUE}========================================${NC}"
print_success "All basic tests completed"
echo ""
echo "Test accounts created:"
echo "  Customer: $CUSTOMER_EMAIL / $CUSTOMER_PASSWORD"
echo "  Vendor: $VENDOR_EMAIL / $VENDOR_PASSWORD"
echo ""
echo "Note: Email verification is required before login."
echo "In development, you can verify emails by:"
echo "  1. Checking application logs for verification tokens"
echo "  2. Manually updating the database"
echo "  3. Using the verification endpoint with the token"
echo ""
echo "Next steps:"
echo "  1. Verify emails using: POST $BASE_URL/auth/verify-email?token=<TOKEN>"
echo "  2. Login using: POST $BASE_URL/auth/login"
echo "  3. Use the access token for authenticated requests"
echo ""
echo -e "${GREEN}Testing complete!${NC}"
