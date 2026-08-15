# Intelligent EV Charging Schedule

> An intelligent electric vehicle charging optimization system that minimizes electricity costs and carbon emissions using real-time market data and advanced algorithms.

A full-stack application demonstrating proficiency in backend optimization algorithms, cloud-native architecture, and modern frontend development.

## Project Highlights

- **Multi-Algorithm Optimization:** Compare 4 distinct scheduling strategies (Naive, Greedy, Dynamic Programming, MIP)
- **Real-Time Market Integration:** Live electricity prices and CO₂ data from Nordic energy markets
- **Admin Analytics:** Randomized benchmark suite with 300-5000 scenario comparisons
- **Production-Ready:** JWT auth, role-based access control, degraded mode fallback, comprehensive testing

## Quick Links

- **Main Project:** See [sdu/README.md](./sdu/README.md) for full documentation
- **Tech Stack:** Java 21, Spring Boot, React, TypeScript, PostgreSQL, Docker
- **Benchmark Results:** DP achieves 98%+ optimality with sub-2ms latency (p95)

## Repository Structure

```
.
├── sdu/                          # Main project directory
│   ├── src/                      # Java backend + React frontend
│   ├── frontend/                 # React TypeScript application
│   ├── pom.xml                   # Maven build configuration
│   ├── compose.yaml              # Docker Compose setup
│   └── README.md                 # Detailed project documentation
└── README.md                     # This file
```

## Getting Started

1. **Read the full documentation:**

   ```bash
   cd sdu
   cat README.md
   ```

2. **Run locally with Docker:**

   ```bash
   cd sdu
   docker-compose up -d
   # Frontend: http://localhost:5173
   # Backend: http://localhost:8080
   ```

3. **Manual setup:**
   - Backend: `cd sdu && ./mvnw spring-boot:run`
   - Frontend: `cd sdu/frontend && npm install && npm run dev`

## Technical Achievements

| Area             | Achievement                                                                  |
| ---------------- | ---------------------------------------------------------------------------- |
| **Algorithms**   | Implemented 4 optimization strategies with performance analysis              |
| **Architecture** | Clean code with strategy pattern, dependency injection, and shared utilities |
| **Testing**      | Randomized benchmarking (300-5000 scenarios), unit tests, integration tests  |
| **Performance**  | DP achieves <2ms response time with 100% optimality guarantee                |
| **Database**     | PostgreSQL with efficient schema for real-time market data queries           |
| **Security**     | JWT authentication, role-based access control, input validation              |
| **DevOps**       | Docker containerization, Maven/npm reproducible builds                       |
| **Frontend**     | React with TypeScript, Tailwind CSS, TanStack Query                          |

## Key Features

- User registration & authentication
- Personalized charging constraints
- Real-time market data integration
- Algorithm comparison dashboard
- Admin benchmark & performance analytics
- Dark/light theme support
- Responsive mobile-friendly UI
- Degraded mode with data fallback

## Performance Metrics

**Latest Benchmark Run (300 scenarios):**

- DP vs Greedy cost gap: **1.47%** (both highly competitive)
- DP latency: **0.92 ms** avg, **2.78 ms** p95
- Greedy latency: **0.09 ms** avg, **0.26 ms** p95
- Algorithm reliability: **99%+** scenarios solved optimally

## Contact

For questions or inquiries about this project, feel free to open an issue or reach out.

---

**For detailed documentation, see [sdu/README.md](./sdu/README.md)**
