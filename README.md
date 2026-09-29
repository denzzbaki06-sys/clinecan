# Clinecan — Phase 3

Sen söyle, Can kodlasın. ☕️

Existing React/TypeScript/Vite coffee workspace and Java 21 / Spring Boot 4.1.1 backend, now with disposable Docker builds, bounded repair, and live SSE progress. OpenAI Java SDK remains 4.69.3. No database, accounts, deployment, payments, or multi-agent framework.

## Architecture

`POST /api/agent/executions` creates a bounded asynchronous execution. `AgentService` still performs ANALYZE → PLAN → GENERATE → VALIDATE through the existing provider-independent `LlmClient`. `ExecutionService` then invokes `SandboxService`, prepares an artifact, and, only for LLM compilation failures, diagnoses and requests up to two structured `RepairPatch` responses before rebuilding.

`OpenAiLlmClient` uses Responses Structured Outputs. Original prompt, specification and plan reach GENERATE. REPAIR receives specification, plan, current files and sanitized diagnostics; only replacement files from the existing manifest are accepted. Merged output passes full manifest, content and dependency validation again. DEMO remains deterministic and request-sensitive; it genuinely builds but never pretends to perform AI repairs.

The old `POST /api/agent/run` is deliberately preserved as the synchronous, **static-only** compatibility API. The frontend uses the new executions API. `/capabilities` distinguishes these paths.

| Endpoint | Behavior |
| --- | --- |
| `POST /api/agent/executions` | 202 with UUID and current snapshot; 429 at capacity |
| `GET /api/agent/executions/{id}` | Current/final state, safe events, source result, build and artifact metadata |
| `GET /api/agent/executions/{id}/events` | SSE `progress` events with ordered IDs, then `complete` snapshot and close |
| `DELETE /api/agent/executions/{id}` | Cancel and interrupt work; container cleanup runs in finally |
| `GET /api/agent/executions/{id}/artifact` | Successful build ZIP as download-only attachment |

SSE replays events after `Last-Event-ID`; connection errors use native reconnect plus final-state recovery. Frontend deduplicates sequence IDs, updates Agent and Terminal live, supports cancellation, and keeps build results in its bounded in-memory history. Four subscribers per execution, 100 events maximum, 20 retained executions, 20-minute retention, 15-minute execution deadline. Cleanup runs every 30 seconds. No persistence across restart.

## Controlled Docker sandbox

Build the **trusted toolchain** once from the repository's `sandbox/` directory:

```sh
docker build -t clinecan-sandbox:phase3 sandbox
```

Image preparation uses Node 24 bookworm-slim and a committed npm lockfile. `npm ci --ignore-scripts` installs only pinned React/ReactDOM 19.3.0, TypeScript 6.0.2, Vite 8.3.1, plugin-react 6.1.1 and React types, plus their locked transitive dependencies. This networked preparation never receives generated projects or API credentials. Per-project builds do **not** install packages or have network access.

The per-project container receives only validated generated files via a read-only temporary `/input` bind. Source is copied into disposable tmpfs. It runs the image-owned `build.sh`, fixed TypeScript compiler and fixed Vite configuration; generated `package.json` commands, Vite config and tsconfig are not executed as build instructions. Only source under `src/` is typechecked; Vite handles the project index/asset imports. No generated shell command is passed to the host or blindly executed.

Dependency metadata is allowlisted. Unsupported packages, URL/file/git dependencies, lifecycle hooks, workspaces, overrides and arbitrary scripts are rejected. Accepted semver metadata does not choose installed versions: the curated image is authoritative. Generated projects needing other libraries must be simplified or the trusted preset deliberately extended by the developer.

Default constraints:

- UID/GID 1000, read-only root, all capabilities dropped, no-new-privileges, default Docker seccomp.
- Network `none`, no published ports, no privileged/host-network mode.
- 512 MiB memory, equal memory+swap limit (no extra swap), 1 CPU, 128 PIDs, 256 open files.
- `/workspace` 256 MiB tmpfs and `/tmp` 64 MiB tmpfs; nosuid/nodev.
- Build timeout 90 seconds (configurable 1–120); Docker startup 15 seconds, export/cleanup 10 seconds each.
- Docker stdout/stderr capture 16k bytes each; returned diagnostics at most 8k characters each. Excess log data is drained and discarded.
- Docker logging disabled; no home, Docker socket, Git/SSH credentials, app repository, API key or application environment mounted/passed into the container.
- `finally` forcibly removes the named container and deletes its temporary input tree without following symlinks. Containers also self-expire after 600 seconds. Startup removes abandoned generated-only directories older than 30 minutes.

Build results include success, exit code, bounded/sanitized stdout/stderr, duration, category and truncation flag. Only compilation failures enter repair; infrastructure, policy and timeout failures stop. Maximum two repair calls and three builds per execution. SDK automatic retries remain disabled.

## Preview and artifacts

**Executable browser preview is intentionally not enabled.** The existing metadata preview remains. A trusted exporter reads only regular output files, rejects symlinks and unsafe names, and bounds output to 64 files, 1 MB per file and 2 MB total. Backend validates paths again and produces an in-memory ZIP. Generated artifacts are never unpacked/executed on the host.

ZIP responses use `attachment`, `application/octet-stream`, `nosniff`, `Cache-Control: no-store`, and a restrictive CSP. No generated HTML/JavaScript is injected into Clinecan, no iframe shares its origin, and no browser security setting was weakened. The UI labels the result as a real successful build plus a downloadable artifact, not a running app.

A dedicated, separately isolated preview origin remains future work. Downloaded artifacts are untrusted code and are not automatically launched.

## Configuration

Spring Boot reads exported environment variables; `.env.example` is documentation, not automatically loaded. Keys stay server-side. Do not use a `VITE_*` variable for an LLM key.

| Variable | Default / bounds |
| --- | --- |
| `CLINECAN_LLM_API_KEY` | empty → DEMO; present → LLM, no silent fallback |
| `CLINECAN_LLM_MODEL` | `gpt-4o-mini`, configurable Responses/Structured Outputs model |
| `CLINECAN_LLM_ENDPOINT` | `https://api.openai.com/v1`; legacy full endpoint paths normalized |
| `CLINECAN_LLM_TIMEOUT_SECONDS` | 45, maximum 60 per provider request |
| `CLINECAN_SANDBOX_ENABLED` | true; false returns explicit SANDBOX_DISABLED on new executions |
| `CLINECAN_SANDBOX_IMAGE` | `clinecan-sandbox:phase3`; trusted prebuilt local image, pull disabled |
| `CLINECAN_SANDBOX_TIMEOUT_SECONDS` | 90, range 1–120 |
| `CLINECAN_SANDBOX_MAX_MEMORY_MB` | 512, range 256–2048 |
| `CLINECAN_SANDBOX_MAX_CPUS` | 1, range 0.25–2 |
| `CLINECAN_SANDBOX_MAX_CONCURRENT` | 2, range 1–4; immediate 429, no unbounded queue |
| `CLINECAN_REPAIR_MAX_ATTEMPTS` | 2, range 0–2 |
| `VITE_AGENT_API_URL` | `http://localhost:8080/api/agent/run`; frontend derives execution endpoints |

Backend binds to 127.0.0.1. This is a trusted local-development application without authentication; do not expose it to other users or a public network.

## Run and verify

This Mac defaults to Java 11; select Java 21 explicitly:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
cd clinecan-backend
./mvnw verify
# Separate opt-in real Docker tests; no OpenAI calls:
./mvnw -Dtest=DockerSandboxIT test
"$JAVA_HOME/bin/java" -jar target/backend-0.0.1-SNAPSHOT.jar
```

In another terminal:

```sh
cd frontend
npm test
npm run lint
npm run build
npm run dev -- --port 5174 --strictPort
```

The working verification preview uses backend 18080 with `--clinecan.allowed-origins=http://localhost:5175`, and frontend `VITE_AGENT_API_URL=http://localhost:18080/api/agent/run npm run dev -- --host localhost --port 5175 --strictPort`. Avoid overwriting a running JAR; stop it or run a temporary copy.

Results: **108 normal backend tests + 4 opt-in real Docker integration tests**, and **21 frontend tests** passed. All prior tests remain. Maven verify, frontend lint and production build passed. Real Docker tests cover task generation/build/artifact, a real TypeScript compilation error, actual timeout/removal, and a real failed-build → stub-LLM patch → successful rebuild. Runtime inspection checked the isolation/resource flags. A stub repair is not a live AI repair.

No OpenAI key was present and no real OpenAI request was made. For a later single smoke execution, define the key privately in your own shell, select Java 21, build the backend/image, and run from repository root:

```sh
python3 scripts/smoke-test.py
# No-cost real Docker alternative:
python3 scripts/smoke-test.py --demo
```

The script never prints the key, starts its own backend on 18082, refuses an occupied port, and shuts down afterward. With no key, the default test skips. Live mode makes one execution (up to three stage requests); repairs are disabled to avoid additional paid calls. DEMO smoke verified COMPLETED, real build success and a 69,768-byte artifact.

## Security limits and remaining work

This is a **development sandbox, not a perfect security boundary**. Docker shares the host/VM kernel, the backend controls a privileged Docker daemon, and container/runtime/dependency vulnerabilities remain possible. The image is locally trusted; maintain and scan it, pin its image ID for stronger reproducibility, and update the floating Node base deliberately. The preset blocks arbitrary dependencies and build configuration and has no install network during untrusted builds.

Abrupt machine/daemon failure can defer container/file cleanup until self-expiry or the next startup. Cleanup depends on Docker/filesystem availability. In-memory state/artifacts disappear on restart and expire after retention. Compiler diagnostics are untrusted, size-limited and sanitized before repair; this reduces prompt-injection exposure but cannot eliminate it. Repairs are always revalidated. A passed build is not proof of functional correctness or application security.

Existing path traversal, duplicate/collision, install-hook, prompt and generated-content limits remain enforced. Source stays data until the isolated worker builds it. Safe metadata Preview executes nothing. No auth, persistence, GitHub push, deployment or payment features were added.

For the interview demo: prebuild the toolchain; run the no-cost smoke; show live DEMO generation, build and artifact; demonstrate the real TypeScript failure/repair fixture tests separately; disclose that live OpenAI was not tested. If you later opt into one live smoke, choose an account-available model and keep the key only in the backend environment. A separate preview origin with restrictive iframe/CSP and independently hardened worker is the next focused step.

Docker reference: [runtime controls](https://docs.docker.com/engine/containers/run/) and [resource limits](https://docs.docker.com/engine/containers/resource_constraints/).
