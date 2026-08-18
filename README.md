# Intelligent EV Charging Scheduler

A full-stack application that creates EV charging schedules from electricity prices, CO2 intensity, vehicle constraints, and a user-selected optimization strategy.

## Highlights

- Four scheduling strategies: naive, greedy, dynamic programming, and mixed-integer programming
- Live Danish market data for DK1 and DK2, with cached-data fallback
- JWT authentication, saved user constraints, and role-protected admin tools
- React and TypeScript dashboard with schedule and market-signal visualizations
- Unit, integration, and randomized algorithm tests

## Technology

- Java 25 and Spring Boot 4
- React 19, TypeScript, and Vite
- PostgreSQL for normal operation; H2 for local development and tests
- Google OR-Tools for mixed-integer optimization

## Repository layout

```text
.
├── backend/              Spring Boot API and backend tests
├── frontend/             React application
├── docs/                 Architecture and design notes
├── .github/workflows/    Continuous integration
├── compose.yaml          Local PostgreSQL service
└── .env.example          Environment variable template
```

Backend and frontend dependencies remain independently managed. A Maven package build also compiles the frontend and embeds its production assets in the Spring Boot artifact.

## Requirements

- Java 25
- Node.js 24 and npm 11
- Docker with Docker Compose, when using PostgreSQL locally

The Maven package build can install its configured Node and npm versions. Direct frontend development still requires Node and npm on your machine.

## Quick start with local H2

The `local` Spring profile uses an ignored H2 database under `backend/.data/` and a development-only JWT secret.

Start the backend:

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

On Windows, use `mvnw.cmd` instead of `./mvnw`.

Start the frontend in another terminal:

```bash
cd frontend
npm ci
npm run dev
```

Open `http://localhost:5173`.

## Run with PostgreSQL

Start PostgreSQL from the repository root:

```bash
docker compose up -d
```

Before starting the backend without the `local` profile, set a strong JWT secret of at least 32 characters:

```bash
export APP_JWT_SECRET="replace-with-a-long-random-secret"
cd backend
./mvnw spring-boot:run
```

PowerShell equivalent:

```powershell
$env:APP_JWT_SECRET = "replace-with-a-long-random-secret"
Set-Location backend
.\mvnw.cmd spring-boot:run
```

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

New accounts receive the `USER` role. To provision an administrator, register the account and promote it through a trusted database session:

```sql
UPDATE app_users SET role = 'ADMIN' WHERE email = 'admin@example.com';
```

## Development commands

Backend tests:

```bash
cd backend
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
cd backend
./mvnw package
```

The scheduling strategy design is documented in [docs/strategy-pattern.md](./docs/strategy-pattern.md).

## Security

- Do not commit `.env` files, `.data/`, database exports, signing keys, or production credentials.
- Production startup requires `APP_JWT_SECRET`; the built-in fallback exists only in the `local` profile.
- Configure `APP_CORS_ALLOWED_ORIGINS` to the exact deployed frontend origins.
- Use a dedicated database account and strong password outside local development.

Report security concerns according to [SECURITY.md](./SECURITY.md).

## License

Licensed under the [MIT License](./LICENSE).
