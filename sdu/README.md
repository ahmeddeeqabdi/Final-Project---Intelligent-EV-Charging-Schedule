# Intelligent EV Charging Scheduler

This project produces cost- and emissions-aware EV charging plans while respecting battery capacity, state of charge, charging power, departure time, and Danish price-area constraints.

## Architecture

The React frontend calls a JWT-protected Spring Boot API. The backend loads electricity prices and CO2 signals, applies the selected scheduling strategy, stores results, and returns a time-slot plan for visualization.

### Scheduling strategies

| Strategy | Purpose | General approach |
| --- | --- | --- |
| Naive | Baseline | Charge immediately within the available window |
| Greedy | Fast planning | Rank candidate slots by weighted cost and emissions |
| Dynamic programming | Discrete optimum | Search feasible energy allocations across time slots |
| MIP | Solver-based comparison | Optimize continuous power allocation with OR-Tools |

Additional design notes are available in [STRATEGY_FACTORY_CLASS_DIAGRAM.md](./STRATEGY_FACTORY_CLASS_DIAGRAM.md).

## Requirements

- Java 25
- Node.js 24 and npm 11
- Docker with Docker Compose, if using PostgreSQL locally

The Maven package build can install its own configured Node and npm versions. Direct frontend development still requires Node and npm on your machine.

## Quick start with local H2

The `local` Spring profile uses an ignored H2 database under `.data/` and a development-only JWT secret.

1. Start the backend from this directory:

   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
   ```

   On Windows, use `mvnw.cmd` instead of `./mvnw`.

2. Start the frontend in another terminal:

   ```bash
   cd frontend
   npm ci
   npm run dev
   ```

3. Open `http://localhost:5173`.

## Run with PostgreSQL

The Compose file starts PostgreSQL only:

```bash
docker compose up -d
```

Before starting the backend without the `local` profile, set a strong JWT secret of at least 32 characters:

```bash
export APP_JWT_SECRET="replace-with-a-long-random-secret"
./mvnw spring-boot:run
```

PowerShell equivalent:

```powershell
$env:APP_JWT_SECRET = "replace-with-a-long-random-secret"
.\mvnw.cmd spring-boot:run
```

The default database connection matches `compose.yaml`. Override it through the environment variables below when needed.

## Configuration

| Variable | Required | Default | Purpose |
| --- | --- | --- | --- |
| `APP_JWT_SECRET` | Yes, except with `local` profile | None | JWT signing secret; use at least 32 characters |
| `APP_CORS_ALLOWED_ORIGINS` | No | `http://localhost:5173` | Comma-separated trusted frontend origins |
| `APP_JWT_EXPIRATION_MS` | No | `3600000` | Token lifetime in milliseconds |
| `DB_URL` | No | `jdbc:postgresql://localhost:5432/mydatabase` | JDBC connection URL |
| `DB_USERNAME` | No | `postgres` | Database user |
| `DB_PASSWORD` | No | `postgres` | Database password for local Compose setup |

Copy [.env.example](./.env.example) when configuring an IDE or deployment environment. Spring Boot does not automatically load `.env` files in a normal shell, so export the variables or configure them through your runtime.

New accounts always receive the `USER` role. To provision an administrator, register the account and promote it through a trusted database session:

```sql
UPDATE app_users SET role = 'ADMIN' WHERE email = 'admin@example.com';
```

## Development commands

Backend tests:

```bash
./mvnw test
```

Frontend checks:

```bash
cd frontend
npm ci
npm run lint
npm run typecheck
npm run build
```

Full Maven package, including the frontend production build:

```bash
./mvnw package
```

## Project structure

```text
sdu/
├── frontend/                       React and TypeScript frontend
├── src/main/java/com/sdu/evcharging/
│   ├── config/                     Security, CORS, and HTTP clients
│   ├── controller/                 REST endpoints
│   ├── domain/                     Persistence and scheduling models
│   ├── repository/                 Spring Data repositories
│   ├── security/                   JWT authentication
│   └── service/                    Authentication, ingestion, and optimization
├── src/main/resources/             Application and database configuration
├── src/test/                       Backend tests
├── compose.yaml                    PostgreSQL development service
└── pom.xml                         Maven configuration
```

## Security notes

- Do not commit `.env` files, `.data/`, database exports, signing keys, or production credentials.
- Production startup requires `APP_JWT_SECRET`; the built-in fallback exists only in the `local` profile.
- Configure `APP_CORS_ALLOWED_ORIGINS` to the exact deployed frontend origins.
- Use a dedicated database account and a strong database password outside local development.

Security concerns should be reported according to [SECURITY.md](../SECURITY.md).

## License

Licensed under the [MIT License](../LICENSE).
