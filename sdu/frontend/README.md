# Frontend

React and TypeScript interface for the Intelligent EV Charging Scheduler.

## Commands

```bash
npm ci
npm run dev
npm run lint
npm run typecheck
npm run build
```

The development server runs at `http://localhost:5173` and proxies API requests to the Spring Boot backend at `http://localhost:8080`.

Set `VITE_API_BASE_URL` only when the API is hosted at a different origin. Local `.env` files are ignored by Git.
