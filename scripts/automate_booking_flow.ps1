# Trevnor Travel Marketplace - Complete Booking Flow Automation Script (PowerShell)
# This script automates the entire flow from login to booking completion

$ErrorActionPreference = "Stop"

$BASE_URL = if ($env:BASE_URL) { $env:BASE_URL } else { "http://localhost:8080" }
$CUSTOMER_EMAIL = "customer1@trevnor.com"
$CUSTOMER_PASSWORD = "password123"

Write-Host "🚀 Starting Trevnor Booking Flow Automation" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Seed Database
Write-Host "Step 1: Seeding database..." -ForegroundColor Blue
try {
    $seedResponse = Invoke-RestMethod -Uri "$BASE_URL/seed" -Method Post
    Write-Host "✅ Database seeded" -ForegroundColor Green
} catch {
    Write-Host "⚠️  Seed may have already run: $_" -ForegroundColor Yellow
}
Write-Host ""

# Step 2: Login Customer
Write-Host "Step 2: Logging in as customer..." -ForegroundColor Blue
$loginBody = @{
    email = $CUSTOMER_EMAIL
    password = $CUSTOMER_PASSWORD
} | ConvertTo-Json

try {
    $loginResponse = Invoke-RestMethod -Uri "$BASE_URL/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
    $TOKEN = $loginResponse.token
    $CUSTOMER_ID = $loginResponse.user.id
    
    Write-Host "✅ Logged in successfully" -ForegroundColor Green
    Write-Host "   Token: $($TOKEN.Substring(0, [Math]::Min(20, $TOKEN.Length)))..." -ForegroundColor Gray
    Write-Host "   Customer ID: $CUSTOMER_ID" -ForegroundColor Gray
} catch {
    Write-Host "❌ Login failed: $_" -ForegroundColor Red
    exit 1
}
Write-Host ""

# Step 3: Search Listings
Write-Host "Step 3: Searching for listings..." -ForegroundColor Blue
$headers = @{
    "Authorization" = "Bearer $TOKEN"
}

try {
    $listingsResponse = Invoke-RestMethod -Uri "$BASE_URL/listings?category=HOTEL&pageSize=1" -Method Get -Headers $headers
    $LISTING_ID = $listingsResponse.listings[0].id
    $LISTING_TITLE = $listingsResponse.listings[0].title
    
    Write-Host "✅ Found listing: $LISTING_TITLE" -ForegroundColor Green
    Write-Host "   Listing ID: $LISTING_ID" -ForegroundColor Gray
} catch {
    Write-Host "❌ No listings found: $_" -ForegroundColor Red
    exit 1
}
Write-Host ""

# Step 4: Check Availability
Write-Host "Step 4: Checking availability..." -ForegroundColor Blue
$CHECKIN_DATE = "2024-12-15T00:00:00Z"
$CHECKOUT_DATE = "2024-12-20T00:00:00Z"
$GUESTS = 2

$availabilityBody = @{
    listingId = $LISTING_ID
    checkInDate = $CHECKIN_DATE
    checkOutDate = $CHECKOUT_DATE
    numberOfGuests = $GUESTS
} | ConvertTo-Json

try {
    $availabilityResponse = Invoke-RestMethod -Uri "$BASE_URL/bookings/check-availability" -Method Post -Body $availabilityBody -ContentType "application/json" -Headers $headers
    
    if (-not $availabilityResponse.available) {
        Write-Host "❌ Listing not available: $($availabilityResponse.reason)" -ForegroundColor Red
        exit 1
    }
    
    $TOTAL_PRICE = $availabilityResponse.priceCalculation.total
    Write-Host "✅ Listing is available" -ForegroundColor Green
    Write-Host "   Total Price: `$$TOTAL_PRICE" -ForegroundColor Gray
} catch {
    Write-Host "❌ Availability check failed: $_" -ForegroundColor Red
    exit 1
}
Write-Host ""

# Step 5: Create Booking
Write-Host "Step 5: Creating booking..." -ForegroundColor Blue
$bookingBody = @{
    listingId = $LISTING_ID
    checkInDate = $CHECKIN_DATE
    checkOutDate = $CHECKOUT_DATE
    numberOfGuests = $GUESTS
    specialRequests = "Late check-in requested"
} | ConvertTo-Json

try {
    $bookingResponse = Invoke-RestMethod -Uri "$BASE_URL/bookings" -Method Post -Body $bookingBody -ContentType "application/json" -Headers $headers
    $BOOKING_ID = $bookingResponse.id
    $BOOKING_STATUS = $bookingResponse.status
    
    Write-Host "✅ Booking created successfully" -ForegroundColor Green
    Write-Host "   Booking ID: $BOOKING_ID" -ForegroundColor Gray
    Write-Host "   Status: $BOOKING_STATUS" -ForegroundColor Gray
} catch {
    Write-Host "❌ Booking creation failed: $_" -ForegroundColor Red
    exit 1
}
Write-Host ""

# Step 6: Create Payment Intent
Write-Host "Step 6: Creating payment intent..." -ForegroundColor Blue
$paymentBody = @{
    bookingId = $BOOKING_ID
    currency = "usd"
} | ConvertTo-Json

try {
    $paymentResponse = Invoke-RestMethod -Uri "$BASE_URL/payments/intent" -Method Post -Body $paymentBody -ContentType "application/json" -Headers $headers
    $PAYMENT_INTENT_ID = $paymentResponse.paymentIntentId
    $CLIENT_SECRET = $paymentResponse.clientSecret
    
    Write-Host "✅ Payment intent created" -ForegroundColor Green
    Write-Host "   Payment Intent ID: $PAYMENT_INTENT_ID" -ForegroundColor Gray
} catch {
    Write-Host "⚠️  Payment intent creation failed (Stripe may not be configured)" -ForegroundColor Yellow
    Write-Host "   Continuing without payment..." -ForegroundColor Yellow
    $PAYMENT_INTENT_ID = $null
}
Write-Host ""

# Step 7: Simulate Payment Success
Write-Host "Step 7: Simulating payment success..." -ForegroundColor Blue
if ($PAYMENT_INTENT_ID) {
    $paymentUpdateBody = @{
        paymentStatus = "PAID"
        paymentId = $PAYMENT_INTENT_ID
    } | ConvertTo-Json
    
    try {
        Invoke-RestMethod -Uri "$BASE_URL/bookings/$BOOKING_ID/payment" -Method Put -Body $paymentUpdateBody -ContentType "application/json" -Headers $headers | Out-Null
        Write-Host "✅ Payment status updated to PAID" -ForegroundColor Green
    } catch {
        Write-Host "⚠️  Payment update failed: $_" -ForegroundColor Yellow
    }
} else {
    Write-Host "⚠️  Skipping payment update (no payment intent)" -ForegroundColor Yellow
}
Write-Host ""

# Step 8: Get Final Booking Status
Write-Host "Step 8: Getting final booking status..." -ForegroundColor Blue
try {
    $finalBooking = Invoke-RestMethod -Uri "$BASE_URL/bookings/$BOOKING_ID" -Method Get -Headers $headers
    $FINAL_STATUS = $finalBooking.status
    $PAYMENT_STATUS = $finalBooking.paymentStatus
    
    Write-Host "✅ Booking Flow Complete!" -ForegroundColor Green
    Write-Host "==========================================" -ForegroundColor Cyan
    Write-Host "   Booking ID: $BOOKING_ID" -ForegroundColor White
    Write-Host "   Status: $FINAL_STATUS" -ForegroundColor White
    Write-Host "   Payment Status: $PAYMENT_STATUS" -ForegroundColor White
    Write-Host "   Listing: $LISTING_TITLE" -ForegroundColor White
    Write-Host "   Check-in: $CHECKIN_DATE" -ForegroundColor White
    Write-Host "   Check-out: $CHECKOUT_DATE" -ForegroundColor White
    Write-Host "   Guests: $GUESTS" -ForegroundColor White
} catch {
    Write-Host "⚠️  Could not get final booking status: $_" -ForegroundColor Yellow
}
Write-Host ""

Write-Host "🎉 Complete booking flow executed successfully!" -ForegroundColor Green

