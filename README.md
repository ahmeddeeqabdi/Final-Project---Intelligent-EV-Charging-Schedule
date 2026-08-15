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

## Getting started

See [sdu/README.md](./sdu/README.md) for setup, configuration, testing, and architecture details.

## Repository layout

```text
.
├── sdu/
│   ├── frontend/          React application
│   ├── src/main/          Spring Boot application
│   ├── src/test/          Backend test suite
│   ├── compose.yaml       Local PostgreSQL service
│   └── pom.xml            Maven build
├── LICENSE
└── README.md
```

## License

Licensed under the [MIT License](./LICENSE).
