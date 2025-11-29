# Trevnor Travel Marketplace API

A comprehensive REST API for the Trevnor Travel Marketplace platform built with Ktor and Kotlin.

## Features

- **User Management**: Registration, authentication, and profile management with role-based access control
- **Travel Listings**: CRUD operations for hotels, flights, activities, and travel packages
- **Booking System**: Complete booking workflow with status and payment tracking
- **Reviews & Ratings**: Review system with verified purchase reviews and automatic rating calculation
- **Search & Filtering**: Advanced search capabilities for travel listings
- **JWT Authentication**: Secure token-based authentication
- **Swagger Documentation**: Interactive API documentation

## API Documentation

### Swagger UI

Once the server is running, access the interactive API documentation at:

**http://localhost:8080/api-docs**

The Swagger UI provides:
- Complete API endpoint documentation
- Interactive API testing (try it out)
- Request/response schemas
- Authentication support (JWT Bearer tokens)

### OpenAPI Specification

The OpenAPI 3.0 specification is available at:

**http://localhost:8080/api-docs/openapi.json**

This JSON file can be:
- Imported into API testing tools (Postman, Insomnia, etc.)
- Used for code generation
- Shared with frontend teams
- Integrated into CI/CD pipelines

### Authentication

Most endpoints require JWT authentication. To authenticate:

1. Register a new user: `POST /auth/register`
2. Or login: `POST /auth/login`
3. Use the returned token in the Authorization header:
   ```
   Authorization: Bearer <your-token>
   ```

In Swagger UI, click the "Authorize" button and enter your token.

### User Roles

- **CUSTOMER**: Can browse listings, make bookings, and leave reviews
- **VENDOR**: Can create and manage travel listings
- **ADMIN**: Full access including user and content moderation

## Building & Running

### Prerequisites

- JDK 17 or higher
- Gradle 9.1 or higher (included via wrapper)

### Seed Test Data

To populate the database with test data for development and testing:

```bash
# Using curl
curl -X POST http://localhost:8080/seed

# Or using the GET endpoint to see what will be seeded
curl http://localhost:8080/seed
```

This creates:
- **5 Test Users**: Admin, 2 Vendors, 2 Customers (all with password: `password123`)
- **6 Sample Listings**: Hotels, flights, activities, and packages

**Test User Credentials:**
- Admin: `admin@trevnor.com` / `password123`
- Vendor 1: `vendor1@trevnor.com` / `password123`
- Vendor 2: `vendor2@trevnor.com` / `password123`
- Customer 1: `customer1@trevnor.com` / `password123`
- Customer 2: `customer2@trevnor.com` / `password123`

### Running the Server

```bash
./gradlew run
```

The server will start on `http://localhost:8080`

### Building

```bash
# Build the project
./gradlew build

# Build a fat JAR (executable with all dependencies)
./gradlew buildFatJar

# Run tests
./gradlew test
```

### Docker

```bash
# Build Docker image
./gradlew buildImage

# Run with Docker
./gradlew runDocker
```

## API Endpoints Overview

### Authentication
- `POST /auth/register` - Register a new user
- `POST /auth/login` - Login and get JWT token
- `GET /auth/me` - Get current user (authenticated)

### Users
- `GET /users/me` - Get current user profile
- `PUT /users/me` - Update current user profile
- `GET /users/{id}` - Get user by ID
- `GET /users` - List all users (ADMIN only)

### Listings
- `GET /listings` - Search and list travel listings (public)
- `POST /listings` - Create a new listing (VENDOR/ADMIN)
- `GET /listings/{id}` - Get listing details (public)
- `PUT /listings/{id}` - Update listing (owner/ADMIN)
- `DELETE /listings/{id}` - Delete listing (owner/ADMIN)
- `GET /listings/vendor/my-listings` - Get vendor's listings

### Bookings
- `POST /bookings` - Create a new booking (authenticated)
- `GET /bookings` - Get user's bookings
- `GET /bookings/{id}` - Get booking details
- `PUT /bookings/{id}/status` - Update booking status (VENDOR/ADMIN)
- `PUT /bookings/{id}/payment` - Update payment status
- `DELETE /bookings/{id}` - Cancel booking

### Reviews
- `POST /reviews` - Create a review (authenticated)
- `GET /reviews/listing/{listingId}` - Get reviews for a listing (public)
- `GET /reviews/my-reviews` - Get user's reviews
- `GET /reviews/{id}` - Get review details
- `PUT /reviews/{id}` - Update review (owner)
- `DELETE /reviews/{id}` - Delete review (owner/ADMIN)
- `PUT /reviews/{id}/approve` - Approve review (ADMIN only)

### Images
- `POST /images/listings/{listingId}` - Upload listing images (multipart/form-data, authenticated)
- `POST /images/users/profile` - Upload profile image (multipart/form-data, authenticated)
- `GET /images/listings/{listingId}` - Get listing images (authenticated)
- `DELETE /images/listings/{listingId}?url={imageUrl}` - Delete image (authenticated)
- `GET /uploads/{subdirectory}/{filename}` - Access uploaded images (public)

## Stripe Payment Setup

### Quick Setup

1. Get your Stripe test keys from [Stripe Dashboard](https://dashboard.stripe.com/test/apikeys)
2. Set environment variables:
   ```bash
   export STRIPE_SECRET_KEY=sk_test_...
   export STRIPE_PUBLISHABLE_KEY=pk_test_...
   export STRIPE_WEBHOOK_SECRET=whsec_...
   ```
3. For local development, use Stripe CLI to forward webhooks:
   ```bash
   stripe listen --forward-to localhost:8080/webhooks/stripe
   ```

## Configuration

Configuration is managed via `src/main/resources/application.yaml`:

```yaml
ktor:
  application:
    modules:
      - com.codewithfk.ApplicationKt.module
  deployment:
    port: 8080

storage:
  driverClassName: org.h2.Driver
  jdbcURL: jdbc:h2:file:./data/trevnor;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE

jwt:
  secret: ${JWT_SECRET?:"your-secret-key-change-in-production-min-256-bits"}
  issuer: trevnor-server
  audience: trevnor-client
  realm: trevnor
```

### Environment Variables

- `JWT_SECRET`: Secret key for JWT token signing (required in production)
- `STRIPE_SECRET_KEY`: Stripe secret API key (required for payments)
- `STRIPE_PUBLISHABLE_KEY`: Stripe publishable API key (for frontend)
- `STRIPE_WEBHOOK_SECRET`: Stripe webhook signing secret (required for webhooks)

## Database

The application supports multiple databases:
- **H2** (default, for development) - No setup required
- **MySQL** (recommended for production)
- **PostgreSQL** (alternative for production)

### Quick Setup

**H2 (Development - Default):**
```bash
# No configuration needed, just run
./gradlew run
```

**MySQL (Production):**
```bash
# Set environment variables
export DB_DRIVER=com.mysql.cj.jdbc.Driver
export DATABASE_URL=jdbc:mysql://localhost:3306/trevnor?useSSL=false&serverTimezone=UTC
export DB_USERNAME=trevnor
export DB_PASSWORD=your_password
```

**PostgreSQL (Production):**
```bash
export DB_DRIVER=org.postgresql.Driver
export DATABASE_URL=jdbc:postgresql://localhost:5432/trevnor
export DB_USERNAME=trevnor
export DB_PASSWORD=your_password
```

See [DATABASE_SETUP.md](./DATABASE_SETUP.md) for detailed setup instructions.

## Project Structure

```
src/main/kotlin/com/codewithfk/
├── models/          # Database models (User, TravelListing, Booking, Review, Category)
├── dto/             # Data transfer objects (Request/Response DTOs)
├── services/        # Business logic (UserService, ListingService, BookingService, ReviewService)
├── routing/         # API routes (AuthRoutes, UserRoutes, ListingRoutes, BookingRoutes, ReviewRoutes)
├── database/        # Database configuration (DatabaseFactory)
├── security/        # JWT configuration (JwtConfig)
└── plugins/         # Ktor plugins (Serialization, Authentication, CORS, StatusPages, Swagger)
```

## Sharing API Documentation

The Swagger documentation can be easily shared with your team:

1. **Direct Link**: Share `http://localhost:8080/api-docs` (when server is running)
2. **OpenAPI Spec**: Share the JSON file at `http://localhost:8080/api-docs/openapi.json`
3. **Export**: Download the OpenAPI spec and import into:
   - Postman
   - Insomnia
   - Swagger Editor (https://editor.swagger.io)
   - API testing tools

## Development

### Adding New Endpoints

1. Create/update DTOs in `src/main/kotlin/com/codewithfk/dto/`
2. Add service methods in `src/main/kotlin/com/codewithfk/services/`
3. Add routes in `src/main/kotlin/com/codewithfk/routing/`
4. Update `openapi.json` with the new endpoint documentation

### Testing

#### Unit Tests

```bash
# Run all tests
./gradlew test

# Run with coverage
./gradlew test jacocoTestReport
```

#### API Testing & Automation

**Postman Collection:**

A complete Postman collection is available for testing the entire booking flow:

1. **Import Collection**: Import `postman/Trevnor_Travel_Marketplace.postman_collection.json`
2. **Import Environment**: Import `postman/Trevnor.postman_environment.json`
3. **Set Base URL**: Update `base_url` variable if needed (default: `http://localhost:8080`)
4. **Run Flow**: Execute requests in order or use Postman Collection Runner

See `postman/BOOKING_FLOW_AUTOMATION.md` for detailed instructions.

**Automation Scripts:**

**Bash Script (Linux/macOS):**
```bash
./scripts/automate_booking_flow.sh
```

**PowerShell Script (Windows):**
```powershell
.\scripts\automate_booking_flow.ps1
```

These scripts automate the complete flow:
1. Seed database
2. Login as customer
3. Search listings
4. Check availability
5. Create booking
6. Create payment intent
7. Process payment
8. Get final booking status

**Customize the script:**
```bash
# Set custom base URL
BASE_URL=http://localhost:8080 ./scripts/automate_booking_flow.sh
```

## License

This project is part of the Trevnor Travel Marketplace platform.

If the server starts successfully, you'll see the following output:

```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```

