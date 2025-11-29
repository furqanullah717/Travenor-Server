#!/bin/bash

# Trevnor Travel Marketplace - Complete Booking Flow Automation Script
# This script automates the entire flow from login to booking completion

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"
CUSTOMER_EMAIL="customer1@trevnor.com"
CUSTOMER_PASSWORD="password123"

echo "🚀 Starting Trevnor Booking Flow Automation"
echo "=========================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Step 1: Seed Database
echo -e "${BLUE}Step 1: Seeding database...${NC}"
SEED_RESPONSE=$(curl -s -X POST "$BASE_URL/seed")
echo "✅ Database seeded"
echo ""

# Step 2: Login Customer
echo -e "${BLUE}Step 2: Logging in as customer...${NC}"
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$CUSTOMER_EMAIL\",\"password\":\"$CUSTOMER_PASSWORD\"}")

TOKEN=$(echo $LOGIN_RESPONSE | grep -o '"token":"[^"]*' | cut -d'"' -f4)
CUSTOMER_ID=$(echo $LOGIN_RESPONSE | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
  echo "❌ Login failed"
  echo "Response: $LOGIN_RESPONSE"
  exit 1
fi

echo -e "${GREEN}✅ Logged in successfully${NC}"
echo "   Token: ${TOKEN:0:20}..."
echo "   Customer ID: $CUSTOMER_ID"
echo ""

# Step 3: Search Listings
echo -e "${BLUE}Step 3: Searching for listings...${NC}"
LISTINGS_RESPONSE=$(curl -s -X GET "$BASE_URL/listings?category=HOTEL&pageSize=1" \
  -H "Authorization: Bearer $TOKEN")

LISTING_ID=$(echo $LISTINGS_RESPONSE | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)
LISTING_TITLE=$(echo $LISTINGS_RESPONSE | grep -o '"title":"[^"]*' | head -1 | cut -d'"' -f4)

if [ -z "$LISTING_ID" ]; then
  echo "❌ No listings found"
  exit 1
fi

echo -e "${GREEN}✅ Found listing: $LISTING_TITLE${NC}"
echo "   Listing ID: $LISTING_ID"
echo ""

# Step 4: Check Availability
echo -e "${BLUE}Step 4: Checking availability...${NC}"
CHECKIN_DATE="2024-12-15T00:00:00Z"
CHECKOUT_DATE="2024-12-20T00:00:00Z"
GUESTS=2

AVAILABILITY_RESPONSE=$(curl -s -X POST "$BASE_URL/bookings/check-availability" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"listingId\":\"$LISTING_ID\",
    \"checkInDate\":\"$CHECKIN_DATE\",
    \"checkOutDate\":\"$CHECKOUT_DATE\",
    \"numberOfGuests\":$GUESTS
  }")

AVAILABLE=$(echo $AVAILABILITY_RESPONSE | grep -o '"available":[^,}]*' | cut -d':' -f2)
TOTAL_PRICE=$(echo $AVAILABILITY_RESPONSE | grep -o '"total":[^,}]*' | cut -d':' -f2)

if [ "$AVAILABLE" != "true" ]; then
  echo "❌ Listing not available"
  echo "Response: $AVAILABILITY_RESPONSE"
  exit 1
fi

echo -e "${GREEN}✅ Listing is available${NC}"
echo "   Total Price: \$$TOTAL_PRICE"
echo ""

# Step 5: Create Booking
echo -e "${BLUE}Step 5: Creating booking...${NC}"
BOOKING_RESPONSE=$(curl -s -X POST "$BASE_URL/bookings" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"listingId\":\"$LISTING_ID\",
    \"checkInDate\":\"$CHECKIN_DATE\",
    \"checkOutDate\":\"$CHECKOUT_DATE\",
    \"numberOfGuests\":$GUESTS,
    \"specialRequests\":\"Late check-in requested\"
  }")

BOOKING_ID=$(echo $BOOKING_RESPONSE | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)
BOOKING_STATUS=$(echo $BOOKING_RESPONSE | grep -o '"status":"[^"]*' | cut -d'"' -f4)

if [ -z "$BOOKING_ID" ]; then
  echo "❌ Booking creation failed"
  echo "Response: $BOOKING_RESPONSE"
  exit 1
fi

echo -e "${GREEN}✅ Booking created successfully${NC}"
echo "   Booking ID: $BOOKING_ID"
echo "   Status: $BOOKING_STATUS"
echo ""

# Step 6: Create Payment Intent
echo -e "${BLUE}Step 6: Creating payment intent...${NC}"
PAYMENT_RESPONSE=$(curl -s -X POST "$BASE_URL/payments/intent" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"bookingId\":\"$BOOKING_ID\",
    \"currency\":\"usd\"
  }")

PAYMENT_INTENT_ID=$(echo $PAYMENT_RESPONSE | grep -o '"paymentIntentId":"[^"]*' | cut -d'"' -f4)
CLIENT_SECRET=$(echo $PAYMENT_RESPONSE | grep -o '"clientSecret":"[^"]*' | cut -d'"' -f4)

if [ -z "$PAYMENT_INTENT_ID" ]; then
  echo "⚠️  Payment intent creation failed (Stripe may not be configured)"
  echo "   Continuing without payment..."
else
  echo -e "${GREEN}✅ Payment intent created${NC}"
  echo "   Payment Intent ID: $PAYMENT_INTENT_ID"
  echo "   Client Secret: ${CLIENT_SECRET:0:20}..."
  echo ""
fi

# Step 7: Simulate Payment Success
echo -e "${BLUE}Step 7: Simulating payment success...${NC}"
if [ ! -z "$PAYMENT_INTENT_ID" ]; then
  PAYMENT_UPDATE_RESPONSE=$(curl -s -X PUT "$BASE_URL/bookings/$BOOKING_ID/payment" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"paymentStatus\":\"PAID\",
      \"paymentId\":\"$PAYMENT_INTENT_ID\"
    }")
  
  echo -e "${GREEN}✅ Payment status updated to PAID${NC}"
else
  echo -e "${YELLOW}⚠️  Skipping payment update (no payment intent)${NC}"
fi
echo ""

# Step 8: Get Final Booking Status
echo -e "${BLUE}Step 8: Getting final booking status...${NC}"
FINAL_BOOKING=$(curl -s -X GET "$BASE_URL/bookings/$BOOKING_ID" \
  -H "Authorization: Bearer $TOKEN")

FINAL_STATUS=$(echo $FINAL_BOOKING | grep -o '"status":"[^"]*' | cut -d'"' -f4)
PAYMENT_STATUS=$(echo $FINAL_BOOKING | grep -o '"paymentStatus":"[^"]*' | cut -d'"' -f4)

echo -e "${GREEN}✅ Booking Flow Complete!${NC}"
echo "=========================================="
echo "   Booking ID: $BOOKING_ID"
echo "   Status: $FINAL_STATUS"
echo "   Payment Status: $PAYMENT_STATUS"
echo "   Listing: $LISTING_TITLE"
echo "   Check-in: $CHECKIN_DATE"
echo "   Check-out: $CHECKOUT_DATE"
echo "   Guests: $GUESTS"
echo ""

# Step 9: Create Review (Optional)
read -p "Create a review? (y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
  echo -e "${BLUE}Step 9: Creating review...${NC}"
  REVIEW_RESPONSE=$(curl -s -X POST "$BASE_URL/reviews" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"listingId\":\"$LISTING_ID\",
      \"bookingId\":\"$BOOKING_ID\",
      \"rating\":5,
      \"title\":\"Amazing experience!\",
      \"comment\":\"Had a wonderful time. Highly recommended!\"
    }")
  
  REVIEW_ID=$(echo $REVIEW_RESPONSE | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)
  if [ ! -z "$REVIEW_ID" ]; then
    echo -e "${GREEN}✅ Review created${NC}"
    echo "   Review ID: $REVIEW_ID"
  fi
fi

echo ""
echo -e "${GREEN}🎉 Complete booking flow executed successfully!${NC}"

