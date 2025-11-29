# Database Setup Guide

The Trevnor Travel Marketplace API supports multiple databases:
- **H2** (default, for development)
- **MySQL** (recommended for production)
- **PostgreSQL** (alternative for production)

## Quick Start

### H2 (Default - Development)

No setup required! H2 is an in-memory/file database that works out of the box.

```bash
# Just run the application
./gradlew run
```

The database file will be created at `./data/trevnor.mv.db`

### MySQL (Production)

#### 1. Install MySQL

**macOS:**
```bash
brew install mysql
brew services start mysql
```

**Linux (Ubuntu/Debian):**
```bash
sudo apt update
sudo apt install mysql-server
sudo systemctl start mysql
sudo systemctl enable mysql
```

**Windows:**
Download and install from [MySQL Downloads](https://dev.mysql.com/downloads/mysql/)

#### 2. Create Database and User

```sql
-- Login to MySQL
mysql -u root -p

-- Create database
CREATE DATABASE trevnor CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Create user (optional, or use root)
CREATE USER 'trevnor'@'localhost' IDENTIFIED BY 'your_password_here';
GRANT ALL PRIVILEGES ON trevnor.* TO 'trevnor'@'localhost';
FLUSH PRIVILEGES;

-- Exit
EXIT;
```

#### 3. Configure Environment Variables

```bash
# Set MySQL connection details
export DB_DRIVER=com.mysql.cj.jdbc.Driver
export DATABASE_URL=jdbc:mysql://localhost:3306/trevnor?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
export DB_USERNAME=trevnor
export DB_PASSWORD=your_password_here
export DB_POOL_SIZE=10
```

#### 4. Update application.yaml (Alternative)

If you prefer to configure in `application.yaml`:

```yaml
storage:
  driverClassName: com.mysql.cj.jdbc.Driver
  jdbcURL: jdbc:mysql://localhost:3306/trevnor?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
  username: trevnor
  password: your_password_here
  maximumPoolSize: 10
```

**⚠️ Warning:** Never commit passwords to version control! Use environment variables.

#### 5. Run the Application

```bash
./gradlew run
```

The tables will be automatically created on first run.

### PostgreSQL (Alternative)

#### 1. Install PostgreSQL

**macOS:**
```bash
brew install postgresql
brew services start postgresql
```

**Linux (Ubuntu/Debian):**
```bash
sudo apt update
sudo apt install postgresql postgresql-contrib
sudo systemctl start postgresql
```

#### 2. Create Database and User

```bash
# Switch to postgres user
sudo -u postgres psql

-- Create database
CREATE DATABASE trevnor;

-- Create user
CREATE USER trevnor WITH PASSWORD 'your_password_here';
GRANT ALL PRIVILEGES ON DATABASE trevnor TO trevnor;

-- Exit
\q
```

#### 3. Configure Environment Variables

```bash
export DB_DRIVER=org.postgresql.Driver
export DATABASE_URL=jdbc:postgresql://localhost:5432/trevnor
export DB_USERNAME=trevnor
export DB_PASSWORD=your_password_here
export DB_POOL_SIZE=10
```

## Environment Variables Reference

| Variable | Description | Default | Example |
|----------|-------------|---------|---------|
| `DB_DRIVER` | JDBC driver class name | `org.h2.Driver` | `com.mysql.cj.jdbc.Driver` |
| `DATABASE_URL` | JDBC connection URL | H2 file URL | `jdbc:mysql://localhost:3306/trevnor` |
| `DB_USERNAME` | Database username | (empty for H2) | `trevnor` |
| `DB_PASSWORD` | Database password | (empty for H2) | `your_password` |
| `DB_POOL_SIZE` | Connection pool size | `10` | `20` |

## JDBC URL Formats

### H2
```
jdbc:h2:file:./data/trevnor;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
```

### MySQL
```
jdbc:mysql://localhost:3306/trevnor?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
```

### PostgreSQL
```
jdbc:postgresql://localhost:5432/trevnor
```

## Connection Pool Settings

The application uses HikariCP for connection pooling with these defaults:
- **Maximum Pool Size**: 10 connections
- **Auto Commit**: false
- **Transaction Isolation**: REPEATABLE_READ

For MySQL, additional optimizations are automatically applied:
- Prepared statement caching
- Batch statement rewriting
- Server-side prepared statements

## Database Schema

The following tables are automatically created on startup:
- `users` - User accounts and profiles
- `travel_listings` - Travel listings (hotels, flights, activities, packages)
- `bookings` - Booking records
- `reviews` - User reviews and ratings
- `categories` - Listing categories

## Migration from H2 to MySQL

If you've been using H2 and want to migrate to MySQL:

1. **Export data from H2** (if needed):
   ```sql
   -- Connect to H2 database
   -- Export data using H2 console or tools
   ```

2. **Set up MySQL** (see steps above)

3. **Update environment variables** to point to MySQL

4. **Run the application** - tables will be created automatically

5. **Import data** (if you exported from H2):
   ```bash
   mysql -u trevnor -p trevnor < exported_data.sql
   ```

## Troubleshooting

### MySQL Connection Issues

**Error: "Access denied for user"**
- Verify username and password
- Check user has privileges: `GRANT ALL PRIVILEGES ON trevnor.* TO 'trevnor'@'localhost';`

**Error: "Unknown database"**
- Create the database: `CREATE DATABASE trevnor;`

**Error: "Public Key Retrieval is not allowed"**
- Add `allowPublicKeyRetrieval=true` to JDBC URL

**Error: "The server time zone value is unrecognized"**
- Add `serverTimezone=UTC` to JDBC URL

### PostgreSQL Connection Issues

**Error: "Connection refused"**
- Check PostgreSQL is running: `sudo systemctl status postgresql`
- Verify port 5432 is accessible

**Error: "password authentication failed"**
- Reset password: `ALTER USER trevnor WITH PASSWORD 'new_password';`

## Production Recommendations

### MySQL
- Use connection pooling (already configured)
- Set appropriate `maximumPoolSize` based on load
- Enable SSL for production: `useSSL=true`
- Use read replicas for scaling
- Regular backups

### PostgreSQL
- Configure `shared_buffers` appropriately
- Set up connection pooling (PgBouncer)
- Enable SSL connections
- Regular backups (pg_dump)

## Security Best Practices

1. **Never commit credentials** to version control
2. **Use environment variables** for all sensitive data
3. **Limit database user privileges** (don't use root)
4. **Enable SSL** for production connections
5. **Use strong passwords**
6. **Restrict database access** to application servers only
7. **Regular security updates** for database software

## Example .env File

Create a `.env` file (don't commit to git):

```bash
# Database Configuration
DB_DRIVER=com.mysql.cj.jdbc.Driver
DATABASE_URL=jdbc:mysql://localhost:3306/trevnor?useSSL=false&serverTimezone=UTC
DB_USERNAME=trevnor
DB_PASSWORD=your_secure_password_here
DB_POOL_SIZE=10

# Other environment variables
JWT_SECRET=your-jwt-secret-key-min-256-bits
STRIPE_SECRET_KEY=sk_test_...
STRIPE_PUBLISHABLE_KEY=pk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
```

Load it before running:
```bash
# Linux/macOS
export $(cat .env | xargs)
./gradlew run

# Or use a tool like dotenv
```

