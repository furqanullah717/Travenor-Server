# Complete Booking Flow Automation Guide

This guide explains how to use the Postman collection to automate the complete booking flow from login to booking completion.

## Prerequisites

1. **Postman Installed** - Download from [postman.com](https://www.postman.com/downloads/)
2. **Server Running** - Start the Trevnor server: `./gradlew run`
3. **Import Collection** - Import the Postman collection and environment files

## Setup Steps

### 1. Import Postman Collection

1. Open Postman
2. Click **Import** button
3. Select `Trevnor_Travel_Marketplace.postman_collection.json`
4. Select `Trevnor.postman_environment.json`
5. Activate the environment: Select "Trevnor Local" from the environment dropdown

### 2. Seed Database

Run the **Setup > 1. Seed Database** request to create test data:
- Test users (admin, vendors, customers)
- Sample listings (hotels, flights, activities, packages)

**Expected Response:**
```json
{
  "message": "Database seeded successfully",
  "testUsers": {
    "admin": {"email": "admin@trevnor.com", "password": "password123"},
    "vendor1": {"email": "vendor1@trevnor.com", "password": "password123"},
    "customer1": {"email": "customer1@trevnor.com", "password": "password123"}
  }
}
```

## Complete Booking Flow

### Step 1: Authentication

**Request:** `Authentication > 2. Login Customer`

This will:
- Login as customer1@trevnor.com
- Save JWT token to `customer_token` variable
- Save user ID to `customer_id` variable

**Variables Set:**
- `customer_token` - JWT token for authenticated requests
- `customer_id` - Customer user ID

### Step 2: Browse Listings

**Request:** `Listings > 1. Search Listings`

This will:
- Search for hotel listings
- Automatically select the first listing
- Save listing ID to `listing_id` variable

**Variables Set:**
- `listing_id` - Selected listing ID
- `listing_price` - Listing base price

### Step 3: Check Availability

**Request:** `Booking Flow > 1. Check Availability`

This will:
- Check if listing is available for selected dates
- Calculate total price (including taxes and fees)
- Save total price to `booking_total` variable

**Request Body:**
```json
{
  "listingId": "{{listing_id}}",
  "checkInDate": "2024-12-15T00:00:00Z",
  "checkOutDate": "2024-12-20T00:00:00Z",
  "numberOfGuests": 2
}
```

**Expected Response:**
```json
{
  "available": true,
  "reason": null,
  "priceCalculation": {
    "basePrice": 1499.95,
    "tax": 149.995,
    "serviceFee": 74.9975,
    "total": 1724.9425,
    "currency": "USD",
    "nights": 5,
    "numberOfGuests": 2
  }
}
```

### Step 4: Create Booking

**Request:** `Booking Flow > 2. Create Booking`

This will:
- Create a booking with selected dates and guests
- Save booking ID to `booking_id` variable
- Booking status will be `PENDING`
- Payment status will be `PENDING`

**Request Body:**
```json
{
  "listingId": "{{listing_id}}",
  "checkInDate": "2024-12-15T00:00:00Z",
  "checkOutDate": "2024-12-20T00:00:00Z",
  "numberOfGuests": 2,
  "specialRequests": "Late check-in requested"
}
```

**Expected Response:**
```json
{
  "id": "uuid",
  "customerId": "uuid",
  "listingId": "uuid",
  "checkInDate": "2024-12-15T00:00:00Z",
  "checkOutDate": "2024-12-20T00:00:00Z",
  "numberOfGuests": 2,
  "totalPrice": 1724.94,
  "currency": "USD",
  "status": "PENDING",
  "paymentStatus": "PENDING",
  "specialRequests": "Late check-in requested"
}
```

### Step 5: Create Payment Intent

**Request:** `Payment Flow > 1. Create Payment Intent`

This will:
- Create a Stripe payment intent
- Save payment intent ID to `payment_intent_id`
- Save client secret to `client_secret` (for frontend Stripe integration)

**Request Body:**
```json
{
  "bookingId": "{{booking_id}}",
  "currency": "usd"
}
```

**Expected Response:**
```json
{
  "clientSecret": "pi_xxx_secret_xxx",
  "paymentIntentId": "pi_xxx",
  "amount": 172494,
  "currency": "usd",
  "status": "requires_payment_method"
}
```

### Step 6: Process Payment (Simulated)

**Request:** `Payment Flow > 3. Simulate Payment Success`

This will:
- Update booking payment status to `PAID`
- Update booking status to `CONFIRMED` (if webhook is configured)
- Complete the booking flow

**Request Body:**
```json
{
  "paymentStatus": "PAID",
  "paymentId": "{{payment_intent_id}}"
}
```

### Step 7: Create Review (Optional)

**Request:** `Reviews > 1. Create Review`

After completing the booking, create a review:

**Request Body:**
```json
{
  "listingId": "{{listing_id}}",
  "bookingId": "{{booking_id}}",
  "rating": 5,
  "title": "Amazing experience!",
  "comment": "Had a wonderful time. Highly recommended!"
}
```

## Running the Complete Flow

### Option 1: Manual Step-by-Step

1. Run requests in order from each folder
2. Variables are automatically saved between requests
3. Check environment variables to see saved values

### Option 2: Postman Collection Runner

1. Click **Run** button on the collection
2. Select all requests in order
3. Click **Run Trevnor Travel Marketplace API**
4. Watch the flow execute automatically

### Option 3: Newman (CLI)

```bash
# Install Newman
npm install -g newman

# Run collection
newman run postman/Trevnor_Travel_Marketplace.postman_collection.json \
  -e postman/Trevnor.postman_environment.json \
  --iteration-count 1
```

## Test Users

After seeding, you can use these test accounts:

| Role | Email | Password |
|------|-------|----------|
| Admin | admin@trevnor.com | password123 |
| Vendor 1 | vendor1@trevnor.com | password123 |
| Vendor 2 | vendor2@trevnor.com | password123 |
| Customer 1 | customer1@trevnor.com | password123 |
| Customer 2 | customer2@trevnor.com | password123 |

## Sample Listings Created

After seeding, you'll have:

1. **Luxury Beach Resort - Maldives** (HOTEL)
   - Price: $299.99/night
   - Capacity: 4 guests
   - Location: Malé, Maldives

2. **Mountain View Hotel - Swiss Alps** (HOTEL)
   - Price: $199.99/night
   - Capacity: 2 guests
   - Location: Zermatt, Switzerland

3. **Round Trip to Paris** (FLIGHT)
   - Price: $899.99
   - Location: New York to Paris

4. **Safari Adventure - Serengeti** (ACTIVITY)
   - Price: $1299.99
   - Capacity: 6 guests
   - Location: Tanzania

5. **Tokyo City Tour** (ACTIVITY)
   - Price: $149.99
   - Capacity: 20 guests
   - Location: Tokyo, Japan

6. **Complete Bali Experience Package** (PACKAGE)
   - Price: $2499.99
   - Capacity: 2 guests
   - Location: Bali, Indonesia

## Environment Variables

The collection automatically manages these variables:

- `base_url` - API base URL (default: http://localhost:8080)
- `customer_token` - JWT token for customer
- `vendor_token` - JWT token for vendor
- `customer_id` - Customer user ID
- `listing_id` - Selected listing ID
- `booking_id` - Created booking ID
- `payment_intent_id` - Stripe payment intent ID
- `client_secret` - Stripe client secret
- `review_id` - Created review ID

## Troubleshooting

### Token Expired
- Re-run the login request to get a new token
- Tokens expire after 24 hours

### Booking Not Available
- Try different dates
- Check if listing has capacity
- Verify listing is active

### Payment Intent Failed
- Ensure Stripe keys are configured
- Check booking exists and belongs to user
- Verify booking amount is correct

## Next Steps

1. **Customize Dates**: Update check-in/check-out dates in requests
2. **Add More Tests**: Create additional test scenarios
3. **CI/CD Integration**: Use Newman in your CI/CD pipeline
4. **Load Testing**: Use Postman's load testing features
5. **Monitor**: Set up monitoring for automated test runs

## API Flow Diagram

```
1. Seed Database
   ↓
2. Login Customer
   ↓
3. Search Listings
   ↓
4. Check Availability
   ↓
5. Create Booking
   ↓
6. Create Payment Intent
   ↓
7. Process Payment
   ↓
8. Booking Confirmed ✅
   ↓
9. Create Review (Optional)
```

## Automation Script Example

You can also create a simple script to run the flow:

```bash
#!/bin/bash

BASE_URL="http://localhost:8080"

# 1. Seed
curl -X POST $BASE_URL/seed

# 2. Login
TOKEN=$(curl -s -X POST $BASE_URL/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"customer1@trevnor.com","password":"password123"}' \
  | jq -r '.token')

# 3. Search listings
LISTING_ID=$(curl -s -X GET "$BASE_URL/listings?category=HOTEL" \
  -H "Authorization: Bearer $TOKEN" \
  | jq -r '.listings[0].id')

# 4. Create booking
BOOKING_ID=$(curl -s -X POST $BASE_URL/bookings \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"listingId\":\"$LISTING_ID\",\"numberOfGuests\":2}" \
  | jq -r '.id')

echo "Booking created: $BOOKING_ID"
```

