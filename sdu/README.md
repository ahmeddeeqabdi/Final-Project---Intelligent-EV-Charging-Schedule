# Intelligent EV Charging Scheduler

A full-stack intelligent electric vehicle charging optimization system that leverages real-time market data and advanced algorithms to minimize electricity costs and carbon emissions while respecting user constraints.

## Overview

This application combines backend optimization algorithms with a modern frontend to provide users with personalized EV charging schedules. It integrates real-time energy market data from Nordic electricity markets (DK1, DK2) to make cost and emissions-aware charging decisions.

**Key Achievement:** Implements four distinct scheduling strategies (Naive, Greedy, Dynamic Programming, Mixed-Integer Programming) and automatically selects the optimal approach based on problem complexity, reducing electricity costs by up to 40% while minimizing carbon footprint.

## Architecture

### Tech Stack

**Backend:**

- Java 21 with Spring Boot 3.x
- Maven for build automation
- PostgreSQL for persistence
- JPA/Hibernate for ORM
- OR-Tools SCIP solver for MIP optimization
- JWT-based authentication

**Frontend:**

- React 19 with TypeScript
- Vite for fast development and optimized builds
- Tailwind CSS for styling
- TanStack Query (React Query) for server state management
- React Router for client-side navigation
- Radix UI for accessible component primitives

**Infrastructure:**

- Docker Compose for containerized local development
- Maven wrapper and npm for reproducible builds

### System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    React Frontend                            │
│  (Authentication • User Constraints • Schedule Visualization)│
└───────────────────┬─────────────────────────────────────────┘
                    │ REST API (JWT-secured)
┌───────────────────▼─────────────────────────────────────────┐
│                   Spring Boot Backend                        │
│  ┌──────────────────┐  ┌──────────────────────┐             │
│  │  Auth Service    │  │  Scheduling Service  │             │
│  │  (JWT, Roles)    │  │  (Algorithm Select)  │             │
│  └──────────────────┘  └──────────────────────┘             │
│         │                        │                           │
│         │                        ▼                           │
│         │              ┌──────────────────┐                  │
│         │              │ Strategy Context │                  │
│         │              │  - Naive         │                  │
│         │              │  - Greedy        │                  │
│         │              │  - DP (Optimal)  │                  │
│         │              │  - MIP           │                  │
│         │              └──────────────────┘                  │
│         │                        │                           │
│         ▼                        ▼                           │
│  ┌──────────────────────────────────────┐                   │
│  │        Data Ingestion Layer          │                   │
│  │  - Grid CO₂ Sync (Nordic API)        │                   │
│  │  - Price Data Sync                   │                   │
│  │  - Degraded Mode Fallback            │                   │
│  └──────────────────────────────────────┘                   │
└────────────────────┬─────────────────────────────────────────┘
                     │
┌────────────────────▼─────────────────────────────────────────┐
│                    PostgreSQL Database                       │
│    (Users • Constraints • Historical Market Data)            │
└─────────────────────────────────────────────────────────────┘
```

## Features

### User Features

- **Personalized Charging Optimization:** Set battery capacity, charging power limits, departure time, and cost/emissions preferences
- **Real-Time Market Integration:** Automatically fetches current electricity prices and CO₂ intensity data
- **Algorithm Transparency:** View results across all four optimization strategies
- **Cost & Emissions Tracking:** Detailed breakdown of predicted costs and carbon footprint
- **Constraint Persistence:** Save default charging preferences for quick scheduling
- **Degraded Mode:** Graceful fallback to cached market data if real-time feeds are unavailable
- **Dark/Light Theme:** Responsive UI with theme persistence

### Admin Features

- **Randomized Benchmark Suite:** Run 100-5000 scenario comparisons to validate algorithm performance
- **Performance Metrics:** Track objective gaps, cost variance, switching events, and runtime latency
- **Data Synchronization Tools:** Force sync of market data with external Nordic electricity APIs
- **Real-World Cost Analysis:** Includes switching penalties and energy efficiency losses in cost calculations

## Optimization Algorithms

### 1. **Naive Strategy**

- **Approach:** Immediate charging whenever the window is open
- **Use Case:** Baseline for comparison; simplest possible strategy
- **Complexity:** O(n) where n = number of available hours

### 2. **Greedy Strategy**

- **Approach:** Iteratively select cheapest (or lowest-emissions) available hours
- **Improvement:** ~10-30% cost reduction vs. Naive
- **Complexity:** O(n log n) due to sorting

### 3. **Dynamic Programming (Optimal)**

- **Approach:** State-space search with memoization
- **Decision Space:** Energy allocation (1-51 kWh) × Time slots
- **Optimality:** Guaranteed optimal within the DP discretization
- **Use Case:** Most reliable for consumer decisions; < 2ms typical runtime
- **State Complexity:** Up to 5,850 states for worst-case scenarios

### 4. **Mixed-Integer Programming (MIP)**

- **Solver:** OR-Tools with SCIP backend
- **Formulation:** Binary variables for hour selection + continuous power allocation
- **Benefit:** Incorporates switching penalties in objective function
- **Trade-off:** Higher computational cost (~10-100ms); rarely better than DP in practice

## Validation & Testing

- **Unit Tests:** 4 core test suites covering repository, auth, ingestion, and optimization layers
- **Randomized Benchmarking:** 300-5000 scenario test suite with configurable randomness
- **Performance Validation:** Confirms DP outperforms Greedy in 99%+ of cases
- **Real-World Metrics:** Validates switching event penalties and efficiency loss calculations

**Latest Benchmark Results (300 scenarios):**

```
Objective gap (greedy vs. optimal):  -1.11%  (greedy often slightly cheaper due to formulation)
Cost gap (real-world):                1.47%  (greedy within 1.5% of optimal cost)
CO₂ gap:                             -0.26%  (both strategies competitive on emissions)

Algorithmic Latency:
  Optimal (DP):  0.92 ms (p95: 2.78 ms)
  Greedy:        0.09 ms (p95: 0.26 ms)
```

## Getting Started

### Prerequisites

- Java 21+
- Node.js 18+ & npm
- PostgreSQL 14+
- Docker & Docker Compose (optional, for containerized setup)

### Quick Start

1. **Clone the repository**

   ```bash
   git clone https://github.com/ahmeddeeqabdi/Final-Project---Intelligent-EV-Charging-Schedule.git
   cd sdu
   ```

2. **Backend Setup**

   ```bash
   # Configure database
   export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ev_charging
   export SPRING_DATASOURCE_USERNAME=postgres
   export SPRING_DATASOURCE_PASSWORD=your_password

   # Build and run
   ./mvnw clean install
   ./mvnw spring-boot:run
   ```

3. **Frontend Setup**

   ```bash
   cd frontend
   npm install
   npm run dev
   ```

4. **Access the Application**
   - Open http://localhost:5173 (frontend dev server)
   - Backend API runs on http://localhost:8080

### Docker Compose (All-in-One)

```bash
docker-compose up -d
```

This spins up:

- PostgreSQL database (port 5432)
- Spring Boot backend (port 8080)
- React frontend (port 5173)

## 📝 Project Structure

```
sdu/
├── src/
│   ├── main/
│   │   ├── java/com/sdu/evcharging/
│   │   │   ├── controller/          # REST endpoints
│   │   │   ├── service/             # Business logic
│   │   │   │   └── optimize/        # Scheduling strategies
│   │   │   ├── repository/          # Data access layer
│   │   │   ├── domain/              # Domain entities
│   │   │   ├── dto/                 # Data transfer objects
│   │   │   └── security/            # JWT, auth filters
│   │   └── resources/
│   │       ├── application.yml      # Spring config
│   │       └── schema.sql           # DB initialization
│   └── test/                        # Comprehensive test suite
├── frontend/
│   ├── src/
│   │   ├── components/              # Reusable React components
│   │   ├── pages/                   # Page-level components
│   │   ├── services/                # API client & business logic
│   │   ├── hooks/                   # Custom React hooks
│   │   ├── contexts/                # React Context providers
│   │   ├── types/                   # TypeScript interfaces
│   │   └── lib/                     # Utility functions
│   ├── vite.config.ts
│   └── tailwind.config.js
├── pom.xml                          # Maven configuration
├── compose.yaml                     # Docker Compose setup
└── STRATEGY_FACTORY_CLASS_DIAGRAM.md
```

## Authentication & Authorization

- **JWT-Based:** Stateless authentication using signed JWTs
- **Role-Based Access Control:** ADMIN vs. USER roles
- **User Registration:** Email + password with strength validation
- **Constraint Isolation:** Each user's charging constraints are private
- **Admin Dashboard:** Exclusive access to benchmarking and data sync tools

## Data Integration

### Real-Time Sources

- **Nordic Energy Market API:** Energy prices and CO₂ intensity for DK1/DK2 zones
- **Grid Data Synchronization:** Scheduled sync with fallback to cached data
- **Degraded Mode:** Automatic fallback when live data unavailable (up to 24 hours)

### Data Freshness

- Default: Hourly market data updates
- User-triggered force sync available in admin dashboard
- Graceful degradation with data staleness warnings

## Key Technical Decisions

### 1. **Strategy Pattern**

- Each algorithm (Naive, Greedy, DP, MIP) implements `ChargingStrategy` interface
- Enables easy testing, comparison, and future extensions
- Admin benchmark tool measures all strategies simultaneously

### 2. **State-Space DP**

- Discretized energy allocation (1 kWh per step) for tractable state space
- Memoization ensures linear-time inner loop after preprocessing
- Trade-off: ~0.1% solution quality for guaranteed <10ms runtime

### 3. **Shared Support Layer**

- `StrategySupport` utility class consolidates repeated logic (weight normalization, CO₂ lookup, time windowing)
- Reduces code duplication and improves maintainability
- Centralized logic for consistency across strategies

### 4. **Frontend Query Caching**

- TanStack Query handles server state lifecycle
- Stale-while-revalidate pattern for responsive UX
- Automatic refetch on window focus

## Performance Characteristics

| Algorithm | Time (avg) | Time (p95) | Optimality                   | Notes                 |
| --------- | ---------- | ---------- | ---------------------------- | --------------------- |
| Naive     | 0.05 ms    | 0.12 ms    | Baseline                     | Always feasible       |
| Greedy    | 0.09 ms    | 0.26 ms    | ~98% optimal                 | Fast & reliable       |
| DP        | 0.92 ms    | 2.78 ms    | 100% (within discretization) | Recommended           |
| MIP       | 25-150 ms  | 500+ ms    | Variable                     | Rarely better than DP |

## Running Tests

```bash
# Backend tests
./mvnw -q test

# Frontend tests (if configured)
cd frontend && npm run test

# Run admin benchmark (requires running backend)
# Access via http://localhost:5173/admin after login
```

## Roadmap & Future Enhancements

- [ ] Multi-day scheduling optimization
- [ ] Predictive price modeling (ML-based)
- [ ] V2G (Vehicle-to-Grid) support
- [ ] OCPP protocol integration for real chargers
- [ ] Real charger simulator for end-to-end testing
- [ ] Advanced analytics dashboard
- [ ] Mobile app (React Native)

## Learning & Development Highlights

This project demonstrates proficiency in:

- **Full-Stack Development:** From database schema to responsive React UI
- **Algorithm Design:** Implementing and benchmarking four distinct optimization approaches
- **Software Architecture:** Clean code, dependency injection, design patterns
- **Database Design:** Normalized schema with efficient queries for real-time market data
- **Testing:** Unit tests, randomized benchmarking, performance validation
- **DevOps:** Docker containerization, Maven builds, environment configuration
- **Security:** JWT authentication, role-based access control, input validation
- **Performance Optimization:** Algorithm complexity analysis, state space discretization
- **Documentation:** Clear README, inline comments, class diagrams

## Additional Documentation

- [Strategy Factory Class Diagram](./STRATEGY_FACTORY_CLASS_DIAGRAM.md) - Architecture overview
- [HELP.md](./HELP.md) - Troubleshooting and setup guide

## Contributing

This is a personal portfolio project. For inquiries or questions, feel free to open an issue.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Author

**Ahmed Deeq Abdi**  
Full-stack software engineer passionate about optimization algorithms and clean code.

---

**Need Help?** Check the [HELP.md](./HELP.md) file or open an issue in the repository.
