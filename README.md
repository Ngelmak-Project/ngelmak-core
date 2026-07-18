# 📱 Ngelmak Thruline Core

**Ngelmak Thruline Core** is the news and information exchange service for the Ngelmak ecosystem. It manages posts, comments, likes, and user interactions, seamlessly integrated with other microservices through the API Gateway.

---

## 📋 Table of Contents

- [Quick Start](#quick-start)
- [Project Structure](#project-structure)
- [Core Components](#core-components)
- [Prerequisites](#prerequisites)
- [Installation & Setup](#installation--setup)
- [Configuration](#configuration)
- [Database Management](#database-management)
- [Performance Tuning](#performance-tuning)
- [Running the Application](#running-the-application)
- [Contributing](#contributing)
- [License](#license)

---

## 🚀 Quick Start

Get up and running in minutes:

```bash
# Clone the repository
git clone https://github.com/yourusername/Ngelmak-Thruline-Core.git
cd Ngelmak-Thruline-Core

# Build the project
mvn clean install

# Run locally (development profile)
mvn spring-boot:run
```

The application will start on `http://localhost:8080`.

---

## 📁 Project Structure

```
Ngelmak-Thruline-Core/
├── src/main/java/.../
│   ├── web/                  # REST API endpoints
│   ├── service/              # Business logic & domain services
│   ├── security/             # Authentication & authorization
│   ├── domain/               # JPA entity models
│   ├── repository/           # Data access layer (JPA)
│   └── config/               # Spring configuration (Vault, DB, messaging)
├── src/main/resources/
│   ├── application.yml       # Default configuration
│   ├── application-prod.yml  # Production configuration
│   └── application-bootstrap.yml  # Initial schema setup
├── Dockerfile                # Container image definition
├── pom.xml                   # Maven build configuration
├── README.md                 # This file
└── .gitignore               # Git ignore rules
```

---

## 🏗️ Core Components

| Component | Version | Purpose |
|-----------|---------|---------|
| **Java** | 21+ | Runtime environment |
| **Spring Boot** | Latest | Application framework |
| **PostgreSQL** | 13+ | Relational data storage |
| **Redis** | 6+ | Caching & session management |
| **SeaweedFS** | Latest | Distributed file/object storage |
| **HashiCorp Vault** | 1.12+ | Secret management (optional) |

---

## ✅ Prerequisites

### Required
- **Java 21+** — [Download](https://openjdk.java.net/)
- **Maven 3.8+** — [Download](https://maven.apache.org/)
- **PostgreSQL 13+** — Running on `localhost:5432` (or configure `postgres:5432` for Docker)
- **Redis 6+** — Running on `localhost:6379` (or configure `redis:6379` for Docker)

### Optional
- **Docker & Docker Compose** — For containerized deployment
- **HashiCorp Vault 1.12+** — For production secret management

---

## 📥 Installation & Setup

### 1. Clone the Repository

```bash
git clone https://github.com/yourusername/Ngelmak-Thruline-Core.git
cd Ngelmak-Thruline-Core
```

### 2. Database Setup

Create the application database and migration user:

```sql
-- Connect to PostgreSQL as superuser
psql -U admin postgres

-- Create the application database
CREATE DATABASE ngelmakdb OWNER postgres;

-- Create the migration user with schema privileges
CREATE ROLE app_migrator WITH LOGIN PASSWORD 'your_unreadable_impossible_remember_password_here';

-- Grant database privileges
GRANT ALL PRIVILEGES ON DATABASE ngelmakdb TO app_migrator;

-- Switch to the new database and grant schema privileges
\c ngelmakdb
GRANT USAGE, CREATE ON SCHEMA public TO app_migrator;
```

### 3. Run Database Migrations

**First time setup only:**

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=bootstrap
```

This uses the `application-bootstrap.yml` profile to create and populate the initial schema.

Alternatively:

```bash
java -jar target/ngelmak-core.jar --spring.profiles.active=bootstrap
```

---

## ⚙️ Configuration

The application uses a **profile-based configuration system** for managing environments.

### Default Configuration (`application.yml`)

Used for development and local testing:

```yaml
spring:
  application:
    name: "Ngelmak Thruline Core"
  
  profiles:
    active: dev  # Switch to 'prod' for production
  
  # ============================================
  # Vault Configuration (Optional)
  # ============================================
  # Uncomment to enable Vault secret management
  # config.import: optional:vault://
  # cloud:
  #   vault:
  #     uri: http://vault:8200
  #     authentication: approle
  #     fail-fast: true
  #     app-role:
  #       role-id: ${VAULT_ROLE_ID}
  #       secret-id: ${VAULT_SECRET_ID}
  #     database:
  #       enabled: true
  #       backend: database
  #       role: ngelmak-springboot-role
  #     kv:
  #       enabled: true
  #       backend: secret
  #       default-context: jjwt/prod
  #       application-name: ""   # No prefix
  #       profile-separator: ""  # Avoids "-dev" or "-prod" suffixes
  
  # ============================================
  # Database Configuration
  # ============================================
  datasource:
    hikari:
      initialization-fail-timeout: 0
    url: jdbc:postgresql://postgres:5432/ngelmakdb
    # username:        # Set via environment variable
    # password:        # Set via environment variable
  
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: update
    show-sql: false
  
  # ============================================
  # Redis Configuration
  # ============================================
  data:
    redis:
      host: redis
      port: 6379
      timeout: 60000ms

# ============================================
# SeaweedFS Configuration
# ============================================
seaweedfs:
  filer:
    url: http://filer:9555

# ============================================
# File Storage & Download Links
# ============================================
file:
  public:
    base-url: https://storage.ngelmak.org
```

---

### Production Configuration (`application-prod.yml`)

Used in production with Vault integration and environment variables:

```yaml
spring:
  profiles:
    active: prod
  
  config:
    import: vault://
  
  cloud:
    vault:
      uri: ${VAULT_URI}
      authentication: approle
      fail-fast: true
      app-role:
        role-id: ${VAULT_ROLE_ID}
        secret-id: ${VAULT_SECRET_ID}
      database:
        enabled: true
        backend: database
        role: ngelmak-springboot-role
      kv:
        enabled: true
        backend: secret
        application-name: ""
        default-context: jjwt/prod
  
  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  
  jpa:
    hibernate:
      ddl-auto: validate  # Never auto-migrate in production
    show-sql: false
  
  data:
    redis:
      host: ${REDIS_HOST}
      port: 6379
```

---

### Bootstrap Configuration (`application-bootstrap.yml`)

**Use this profile only once** for initial schema creation:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ngelmakdb
    username: app_migrator
    password: your_secure_password_here
  
  jpa:
    hibernate:
      ddl-auto: update  # Create/update schema
    show-sql: true
```

---

## 🔑 Vault Configuration (Optional)

For production deployments, use **HashiCorp Vault** to securely manage secrets.

### Prerequisites

- Vault server running and unsealed
- AppRole authentication enabled
- KV v2 secrets engine mounted at `secret/`
- Database secrets engine configured
- Transit engine enabled (optional)

### Setting Up JWT Secret

Store the JWT secret in Vault under `secret/jjwt/prod`:

```bash
vault kv put secret/jjwt/prod jwt-secret-key="your-secure-jwt-secret-here"
```

Verify it was stored:

```bash
vault kv get secret/jjwt/prod

========= Data =========
Key               Value
---               -----
jwt-secret-key    your-secure-jwt-secret-here
```

The application will automatically load this into:

```java
@Value("${jwt-secret-key}")
private String jwtSecretKey;
```

### Vault Policy

Create a policy file (`springboot-policy.hcl`) to restrict Spring Boot's access:

```bash
tee /etc/openbao/policies/auth-app-policy.hcl <<EOF
# Allow reading database dynamic credentials
path "database/creds/ngelmak-springboot-role" {
  capabilities = ["read"]
}

# Allow reading JWT secrets under secret/jjwt/*
path "secret/jjwt/*" {
  capabilities = ["read"]
}
EOF
```

Apply the policy:

```bash
vault policy write springboot ./springboot-policy.hcl
```

### AppRole Setup

Generate AppRole credentials for Spring Boot authentication:

```bash
# View the role-id
vault read auth/approle/role/springboot/role-id

# Generate a new secret-id
vault write -f auth/approle/role/springboot/secret-id

# Export as environment variables
export VAULT_ROLE_ID="..."
export VAULT_SECRET_ID="..."
export VAULT_URI="https://vault.ngelmak.org"
```

---

## 🗄️ Database Management

### Initial Setup

Create the database and user (run once):

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=bootstrap
```

### Running Migrations

For subsequent deployments, migrations run automatically on startup. To disable auto-migration (recommended for production), set in `application-prod.yml`:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # Only validate, never modify schema
```

---

## 📊 Performance Tuning

### Feed Query Indexes

Optimize timestamp-based feed queries to prevent full table scans on large datasets:

```sql
-- Broader recent index (covers most queries)
CREATE INDEX idx_post_recent ON post (at DESC) 
WHERE at >= NOW() - INTERVAL '6 months';

-- Optional: Only if you frequently query 1+ years back
CREATE INDEX idx_post_historical ON post (at DESC) 
WHERE at >= NOW() - INTERVAL '1 year';
```

### Full-Text Search

Enable fast, relevance-ranked search across post content using PostgreSQL's built-in full-text search:

---

Add the tsvector column (normal column, not generated)
```sql
-- Drop column if exists
ALTER TABLE post DROP COLUMN IF EXISTS textsearchable_index_col;
-- Add a generated tsvector column for French language search
ALTER TABLE post
ADD COLUMN textsearchable_index_col tsvector;
```

---

Create the trigger function. This function:

- loads the channel name via a join  
- updates the tsvector **only if visible = true**  
- sets the tsvector to `NULL` when the post is not visible (so it won’t match anything)

```sql
-- Drop function if exists
DROP FUNCTION IF EXISTS post_tsvector_update();
-- Create function
CREATE OR REPLACE FUNCTION post_tsvector_update() 
RETURNS trigger AS $$
DECLARE
    chan_name TEXT;
BEGIN
    -- Fetch channel name
    SELECT name INTO chan_name
    FROM channel
    WHERE id = NEW.channel_id;

    -- Only index visible posts
    IF NEW.visible IS TRUE THEN
        NEW.textsearchable_index_col :=
            setweight(to_tsvector('french', NEW.content), 'A') ||
            setweight(to_tsvector('french', coalesce(NEW.keywords, '')), 'D') ||
            setweight(to_tsvector('french', coalesce(chan_name, '')), 'B');
    ELSE
        NEW.textsearchable_index_col := NULL;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

---

Create the trigger. This trigger fires **only when relevant fields change**:

- `content`
- `keywords` (optional)

```sql
-- Drop trigger if exists
DROP TRIGGER IF EXISTS trg_post_tsvector_update ON post;
CREATE TRIGGER trg_post_tsvector_update
BEFORE INSERT OR UPDATE OF content
ON post
FOR EACH ROW
EXECUTE FUNCTION post_tsvector_update();
```

---

Create the GIN index  
```sql
-- Drop index if exists
DROP INDEX IF EXISTS idx_post_textsearch;
-- Create index
CREATE INDEX idx_post_textsearch
ON post USING GIN (textsearchable_index_col);
```

---

**Example query:**

```sql
SELECT id, content, ts_rank_cd(textsearchable_index_col, query) AS rank
FROM post,
     websearch_to_tsquery('french', 'your-search-term') AS query
WHERE status = 'VALIDATED'
  AND textsearchable_index_col @@ query
ORDER BY rank DESC
LIMIT 10;
```

---

## 🚀 Running the Application

### Local Development

```bash
# Development mode (auto-reload)
mvn spring-boot:run
```

The application starts at `http://localhost:8080`.

### Docker Compose

Run the entire stack locally:

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: ngelmakdb
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data

  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: dev
    depends_on:
      - postgres
      - redis

volumes:
  postgres_data:
  redis_data:
```

Start services:

```bash
docker-compose up -d
```

### Production Deployment

```bash
# Build the JAR
mvn clean package

# Run with production profile
java -jar target/ngelmak-core.jar \
  --spring.profiles.active=prod \
  --server.port=8080
```

---

## 🤝 Contributing

Contributions are welcome! Please follow these guidelines:

1. **Fork** the repository
2. **Create a feature branch** (`git checkout -b feature/amazing-feature`)
3. **Commit your changes** (`git commit -m 'Add amazing feature'`)
4. **Push to the branch** (`git push origin feature/amazing-feature`)
5. **Open a Pull Request**

### Code Style

- Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- Use meaningful commit messages
- Write tests for new features

---

## 📄 License

This project is licensed under the **GNU General Public License v3.0** — see the [LICENSE](LICENSE) file for details.

You are free to:
- **Use** this software for any purpose
- **Copy** and distribute it
- **Modify** and distribute modified versions

Under the condition that you:
- **Disclose** the source code
- **License** derivative works under GPLv3
- **Include** a copy of this license

For the full license text, visit [gnu.org/licenses/gpl-3.0.html](https://www.gnu.org/licenses/gpl-3.0.html)
---

## 📞 Support

For issues, questions, or suggestions:

- **GitHub Issues**: [Report a bug](https://github.com/yourusername/Ngelmak-Thruline-Core/issues)
- **Discussions**: [Join the conversation](https://github.com/yourusername/Ngelmak-Thruline-Core/discussions)

---

## 🔗 Related Projects

- [Ngelmak API Gateway](https://github.com/yourusername/Ngelmak-API-Gateway)
- [Ngelmak User Service](https://github.com/yourusername/Ngelmak-User-Service)
- [Ngelmak Infrastructure](https://github.com/yourusername/Ngelmak-Infrastructure)