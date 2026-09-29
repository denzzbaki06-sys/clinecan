# Clinecan frontend

React + TypeScript + Vite workspace. See the root README for Java/backend setup and current MVP limitations.

```sh
npm ci
npm run dev -- --port 5174 --strictPort
npm run lint
npm test
npm run build
```

Set `VITE_AGENT_API_URL` only when using a backend other than localhost:8080. The corresponding frontend origin must be allowed by the backend's `clinecan.allowed-origins` property.

Components live in `src/components`, the typed HTTP client in `src/services/agentApi.ts`, and response types in `src/types/agent.ts`. Palette tokens are in `src/index.css`; responsive layout styles are in `src/App.css`.

Phase 2 adds `execution.ts` for server event adaptation and honest mode labels, and `previewProvider.ts` for safe metadata previews. Tests use Node's built-in runner without adding app dependencies. LLM configuration belongs only to Spring Boot; see the root README.
