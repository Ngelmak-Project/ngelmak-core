# 📱 Ngelmak Thruline Core

**Ngelmak Thruline Core** is the news/information exchange service for the Ngelmak ecosystem. It manages posts, comments, likes, and user interactions, integrated with other microservices through the API Gateway.

---

## 📁 Project Structure

```
Ngelmak-Thruline-Core/
├── src/main/java/.../thruline/
│   ├── web/           # 🔌 REST endpoints
│   ├── service/       # 🧠 Business logic
│   ├── security/      # 🔐 Security & auth
│   ├── domain/        # 📦 Entity models
│   ├── repository/    # 🗂️ JPA repositories
│   └── config/        # ⚙️ Vault, DB, messaging
├── src/main/resources/
│   ├── application.yml
│   ├── application-prod.yml
│   └── application-bootstrap.yml
├── Dockerfile
└── pom.xml
```

---

## 🏗️ Core Components

| Component | Purpose |
|-----------|---------|
| **PostgreSQL** | 🗄️ Persistent relational data storage |
| **Redis** | ⚡ Caching & session management |
| **SeaweedFS** | 📂 Distributed file/object storage |
| **HashiCorp Vault** | 🔑 Secret management (optional) |
| **Spring Boot** | 🚀 Application framework (Java 21+) |

---

## ✅ Prerequisites

- **Java 21+** and **Maven 3.8+**
- **PostgreSQL** running on `postgres:5432` (or `localhost:5432`)
- **Redis** running on `redis:6379` (or `localhost:6379`)
- **Vault** running on `vault:8200` (optional; `localhost:8200` for local dev)

---

## 🗄️ Database Setup

Create the application database and migration user:

```sql
-- Create the application database
CREATE DATABASE ngelmakdb OWNER postgres;

-- Create the migration user with schema update privileges
CREATE ROLE app_migrator WITH LOGIN PASSWORD 'your_password_here';
GRANT ALL PRIVILEGES ON DATABASE ngelmakdb TO app_migrator;

-- Grant schema privileges
\c ngelmakdb
GRANT USAGE, CREATE ON SCHEMA public TO app_migrator;
```

---

## ⚙️ Configuration Files

### `application.yml` (Default / Runtime)

This is the file Spring Boot loads automatically for normal operation.

```yaml
spring:
  application:
    name: "Ngelmak Project - Core"
  
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
  #       backend: kv
  #       default-context: jjwt
  #       profile-separator: "/"
  
  # ============================================
  # Database Configuration
  # ============================================
  datasource:
    hikari:
      # Don't fail startup if DB is temporarily unavailable
      # Useful for dev/test environments
      initialization-fail-timeout: 0
    # PostgreSQL connection URL
    # Use 'postgres' as service name in Docker Compose
    url: jdbc:postgresql://postgres:5432/ngelmakdb
   #  username: 
   #  password:
  
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      # 'update' = auto-migrate schema; use application-bootstrap.yml for initial setup
      ddl-auto: update
    # Disable SQL logging in production for performance
    show-sql: false
  
  # ============================================
  # Redis Configuration
  # ============================================
  data:
    redis:
      # Redis service name (Docker) or localhost (local dev)
      host: redis
      port: 6379
      # Connection timeout in milliseconds
      timeout: 60000ms

# ============================================
# SeaweedFS Configuration
# ============================================
seaweedfs:
  filer:
    # Internal URL for backend → SeaweedFS communication
    # Use service name 'filer' in Docker Compose
    url: http://filer:9555

# ============================================
# File Storage & Download Links
# ============================================
file:
  public:
    # Public URL for client-side file downloads
    # Typically routed through CDN or reverse proxy
    base-url: https://storage.ngelmak.org
```

### `application-bootstrap.yml` (🔨 Schema Creation)

**Use this profile only once** for initial database schema creation and migration. Run with the `app_migrator` user who has ALTER privileges.

```yaml
spring:
  datasource:
    # Local PostgreSQL connection for schema migration
    url: jdbc:postgresql://localhost:5432/ngelmakdb
    # User with ALTER/CREATE privileges on schema
    username: app_migrator
    password: your_password_here
  
  jpa:
    hibernate:
      # 'update' = create/modify tables
      # 'create' = drop and recreate (development only!)
      # 'create-drop' = reset on every restart (testing only!)
      ddl-auto: update
    # Show generated SQL for debugging
    show-sql: true
```

**Run with:**
```bash
# Maven
mvn spring-boot:run -Dspring-boot.run.profiles=bootstrap

# JAR
java -jar app.jar --spring.profiles.active=bootstrap
```

### `application-prod.yml` (Production)

Use environment variables and Vault for all secrets in production. Never commit credentials.

```yaml
spring:
  profiles:
    active: prod
  
  # Retrieve all secrets from Vault
  config.import: vault://
  cloud:
    vault:
      uri: ${VAULT_URI}
      authentication: approle
      fail-fast: true
      app-role:
        role-id: ${VAULT_ROLE_ID}
        secret-id: ${VAULT_SECRET_ID}
  
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

## 🔑 Vault Configuration (Optional)

### Prerequisites

- Vault server running and unsealed
- Initial root token or authenticated session
- [Vault CLI installed](https://www.vaultproject.io/downloads)
- AppRole auth method enabled on Vault
- Database backend configured for dynamic credentials
- KV secrets engine configured

---

### Configuration Parameters

#### `uri`
**What it is:** The network address where Vault is accessible.  
**Value:** `http://vault:8200` (Docker) or `http://localhost:8200` (local dev)

#### `authentication: approle`
**What it is:** Authentication method. AppRole is ideal for automated/application authentication without human tokens.  
**Prerequisite:** AppRole auth method must be [enabled on Vault](https://www.vaultproject.io/docs/auth/approle).

#### `fail-fast: true`
**What it is:** If `true`, Spring Boot crashes immediately if Vault is unreachable at startup. If `false`, app starts anyway (useful for development fallback).  
**Recommended:** `true` in production, `false` in development.

#### `app-role`
**What it is:** AppRole credentials for authenticating the application to Vault.

- **`role-id`**: Fixed identifier for the AppRole (retrieve with `vault read auth/approle/role/springboot/role-id`)
- **`secret-id`**: Temporary secret issued per deployment (retrieve with `vault write -f auth/approle/role/springboot/secret-id`)

**Set as environment variables:**
```bash
export VAULT_ROLE_ID="your_role_id_here"
export VAULT_SECRET_ID="your_secret_id_here"
```

#### `database`
**What it is:** Vault's dynamic database credentials backend. Generates temporary PostgreSQL usernames/passwords automatically.

- **`enabled: true`**: Enable dynamic credential generation
- **`backend: database`**: Use Vault's database secrets engine
- **`role: ngelmak-springboot-role`**: Vault role name that defines which database and permissions the app gets

**Prerequisite:** Database backend must be configured in Vault with a role named `ngelmak-springboot-role` that points to your PostgreSQL instance.

**How to retrieve credentials at runtime:**  
Spring Cloud Vault automatically reads from `database/creds/ngelmak-springboot-role` and injects them into `spring.datasource.username` and `spring.datasource.password`.

#### `kv`
**What it is:** Vault's Key-Value secrets engine for storing static configuration (API keys, JWT secrets, etc.).

- **`enabled: true`**: Enable KV secret retrieval
- **`backend: kv`**: Use KV v2 secrets engine (or `kv-v1` for legacy)
- **`default-context: jjwt`**: Path prefix in Vault (e.g., `secret/jjwt/`)
- **`profile-separator: "/"`**: Separates profile-specific secrets (e.g., `secret/jjwt/dev` or `secret/jjwt/prod`)

**Prerequisite:** KV secrets engine must be mounted at `secret/` path with secrets stored like:
```bash
vault kv put secret/jjwt/dev jwt-secret=my_dev_secret
vault kv put secret/jjwt/prod jwt-secret=my_prod_secret
```

Spring Cloud Vault automatically injects these into `@Value` fields or `@ConfigurationProperties`.

---

### How to Get Credentials

```bash
# 1. Get the AppRole Role ID (stable)
vault read auth/approle/role/springboot/role-id

# 2. Generate a Secret ID (temporary, usually 72h TTL)
vault write -f auth/approle/role/springboot/secret-id

# 3. Export as environment variables
export VAULT_ROLE_ID="c481309c-8927-83b8-92a3-771d312e4905"
export VAULT_SECRET_ID="c78ee677-3b49-e5a8-9b91-810c1d768fa9"
```

For complete Vault setup, policy configuration, and role creation, see [Ngelmak-Vault](https://github.com/Ngelmak-Project/ngelmak-vault).

---

## 🚀 Run Locally

```bash
mvn spring-boot:run
```

The service starts on the default Spring Boot port (typically `8080`).

---

## 📊 Database Performance Tuning

### Feed Query Indexes

**Why:** Efficient timestamp-based filtering prevents full table scans on large datasets.

```sql
-- Primary index for feed queries (most important)
CREATE INDEX idx_post_at ON post (at DESC);

-- Optional: Include engagement metrics for scoring
CREATE INDEX idx_post_at_comment ON post (at DESC, comment_count DESC);

-- Optional: Index only recent posts to reduce index size
CREATE INDEX idx_post_recent ON post (at DESC) 
WHERE at >= NOW() - INTERVAL '30 days';
```

### Full-Text Search Setup

**Why:** Enables fast, relevance-ranked search across post content without scanning entire tables.

Add a generated `tsvector` column for French language search:

```sql
ALTER TABLE post
ADD COLUMN textsearchable_index_col tsvector
GENERATED ALWAYS AS (
    setweight(to_tsvector('french', content), 'A') ||
    setweight(to_tsvector('french', coalesce(keywords, '')), 'D')
) STORED;

-- GIN index for fast full-text queries
CREATE INDEX post_textsearch_idx ON post USING GIN (textsearchable_index_col);
```

**Query example:**
```sql
SELECT id, content, ts_rank_cd(textsearchable_index_col, query) AS rank
FROM post,
     websearch_to_tsquery('french', 'hello') AS query
WHERE status = 'VALIDATED'
  AND textsearchable_index_col @@ query
ORDER BY rank DESC
LIMIT 10;
```

---

## 📜 License

MIT License