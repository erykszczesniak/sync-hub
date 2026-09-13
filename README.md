# sync-hub

A System-to-System Sync Hub: it keeps a destination (System B) in sync with a source (System A)
that has a different data model and never talks to it directly. Incremental extraction over REST and
GraphQL, a canonical model in the middle, idempotent loading, schema-drift quarantine, CDC-style
change events on Kafka, and a React dashboard that shows the whole flow's health.

Kotlin + Spring Boot on the backend, React + TypeScript on the front, everything in one
`docker compose up`.

## 1. Overview

```
                 ┌──────────────────────────────── sync-hub ───────────────────────────────┐
                 │                                                                          │
 System A        │  source            transform               sink            events       │      Kafka
 (CRM-like)      │  ┌──────────┐      ┌──────────────┐        ┌──────────┐    ┌─────────┐  │   ┌──────────────────┐
 REST customers ─┼─►│ extract  │─────►│ drift check  │──ok───►│ map to B │───►│ outbox  │──┼──►│ synchub.changes.*│
 GraphQL orders ─┼─►│ (pages,  │      │ + canonical  │        │ idempot. │    │ publish │  │   └──────────────────┘
                 │  │ watermark)│     │   model      │──drift─┐ merge    │    └─────────┘  │
                 │  └──────────┘      └──────────────┘        │└────┬─────┘                │
                 │        ▲                                    ▼     ▼                      │
                 │  ┌─────┴──────────────────────────────────────────────────┐              │
                 │  │ hub state: watermarks · run log · quarantine · drift   │  PostgreSQL  │
                 │  │ System B: b_customer · b_order                          │  (System B)  │
                 │  └────────────────────────────────────────────────────────┘              │
                 │                            │ read-only status API                        │
                 └────────────────────────────┼──────────────────────────────────────────────┘
                                              ▼
                                 React dashboard: feed health, runs, lag,
                                 drift alerts, quarantine, MTTD / MTTR
```

**The integration story.** System A is a CRM-like service with nested addresses, tags and
camelCase timestamps. System B is a warehouse-style destination with flat rows, codes and ranks.
The hub extracts only what changed in A since its last watermark, validates and normalises it into a
neutral canonical model, merges it into B keyed on a stable business key (so re-runs, backfills and
late records never duplicate or overwrite newer state), publishes one change event per applied
change, and refuses to guess when A's contract drifts: drifted records are quarantined with their
raw payload, an event is raised, and an operator replays them once the cause is fixed.

Because a live partner API is not practical for a portfolio repository, System A is simulated by a
small Spring Boot service in this repo (`system-a-sim`) with a realistic REST change feed, a GraphQL
orders connection, API-key auth, deterministic seed data and switches that make its schema drift.

**The integration / ETL angle.** The project is deliberately shaped around what integration and ETL
roles screen for: API integration (REST + GraphQL, auth, pagination, resilience), schema mapping
across systems through a canonical model, incremental / CDC patterns with watermarks, idempotency,
schema-drift handling, data quality, and observability.

## 2. Tech stack

| Layer | Technology | Version |
| --- | --- | --- |
| Language / runtime | Kotlin on JDK 21 | Kotlin 2.2.20 |
| Backend | Spring Boot (Web, Data JPA, Validation, Security, Scheduling, Kafka) | 3.5.16 |
| Outbound calls | Spring WebClient, Spring GraphQL `HttpGraphQlClient` | Boot-managed |
| Resilience | resilience4j (retry, rate limiter, circuit breaker) | 2.3.0 |
| Messaging | Apache Kafka (KRaft, single node in compose) | 3.9.1 |
| Persistence | PostgreSQL (System B + hub state), H2 for dev/tests, Flyway | PostgreSQL 16, Flyway 11 |
| Mapping | Explicit Kotlin mappers (see §3) | – |
| API docs | springdoc-openapi (Swagger UI) | 2.8.9 |
| Dashboard | React, Vite, TypeScript, TanStack Query, Recharts | React 19.2, Vite 8, TS 6, Recharts 3 |
| Build | Gradle (Kotlin DSL) wrapper, npm | Gradle 8.14.3, Node 26 |
| Tests | JUnit 5, MockK, Spring Boot Test, WireMock, Testcontainers (PostgreSQL + Kafka), Vitest + Testing Library | – |
| Quality | ktlint, detekt, JaCoCo; ESLint, Prettier, tsc | – |
| Containers | Docker, docker compose | – |

## 3. Architecture

### Modules

| Module | What it is |
| --- | --- |
| `hub/` | The integration core (Spring Boot). Packages: `source` (System A clients + extraction), `canonical` (neutral model), `transform` (mappers, drift detection), `sink` (System B model + idempotent loaders), `sync` (orchestration, watermarks, quarantine, drift events), `events` (outbox + Kafka), `status` (read-only API + remediation), `repository` (JPA), `common`, `config`. |
| `system-a-sim/` | The simulated source: REST change feed, GraphQL orders, admin/demo controls, drift switches. |
| `dashboard/` | The read-only observability UI. |

Dependency direction inside the hub: `source / sink / events / status → sync → transform → canonical`.
`canonical` depends on nothing application-specific.

### The canonical model in the middle

System A is never mapped straight to System B. Every source maps *into* the canonical model
(`CanonicalCustomer`, `CanonicalOrder`, with explicit Kotlin validation that reports every violation
at once), and every sink maps *out of* it. A third system is one more mapper, not a rewrite.

Mapping is written as explicit Kotlin mappers rather than MapStruct: with data classes a mapping is a
constructor call, and the interesting parts (collecting every problem, normalisation, money in minor
units) need custom code anyway. Every line is readable and unit-tested.

### Incremental extraction and watermarks

Each feed keeps a watermark (the source `updatedAt` up to which changes were processed). An
incremental run reads `updatedAt > watermark − overlap` (default 5 s): sources rarely guarantee
monotonic timestamps across concurrent writers, and because loading is idempotent, re-reading a small
overlap costs a few skipped records and buys "no change is ever missed". A failed run leaves the
watermark alone; backfills never move it. Extraction is always paged and bounded.

### Idempotent loading, late arrivals and backfills

System B rows are keyed on the business key and carry the source's version clock, a content
fingerprint and a hub-side version counter. The merge rules (`AbstractSinkLoader`):

1. one row per key, never a duplicate;
2. an incoming version older than the stored one is `SKIPPED_STALE` (late arrivals cannot overwrite
   newer state; equal clocks apply, last writer wins);
3. same content is `SKIPPED_UNCHANGED` with no write that matters and no event, so replays and
   backfills are free;
4. deletes are soft, so a stale delete or a later resurrection is still decidable.

### Schema drift is explicit

Every raw payload is compared with the feed's `SchemaContract` before mapping. Missing, retyped,
renamed fields and values outside a closed domain are **blocking**: the record is quarantined with its
raw payload and a drift event is raised (one per distinct finding, not per record). Added fields are
a **warning**: the record loads, the event tells an operator the source grew. Full policy, metrics
and the resolution flow: [`docs/SCHEMA-DRIFT.md`](docs/SCHEMA-DRIFT.md).

### Delivery semantics

Change events go through a transactional outbox: the System B write and the outbox row commit
together, the publisher marks a row published only after Kafka acknowledged it. The guarantee is
**at-least-once, ordered per key**; consumers deduplicate on `eventId` or apply only if `version` is
newer. Details and payload: [`docs/DELIVERY-SEMANTICS.md`](docs/DELIVERY-SEMANTICS.md).

### Observability

Every run writes a run-log entry (extracted / transformed / loaded / skipped / quarantined / failed,
duration, watermark before and after, max lag, drift flag, error). Logs carry `run=<id> feed=<name>`.
The status API derives feed health, freshness, lag, MTTD and MTTR from that state; the dashboard only
reads it.

## 4. Prerequisites

- Docker with the compose plugin (the whole demo), or
- JDK 21 and Node 26 for running the modules directly (the Gradle wrapper is in the repo).

## 5. Quick start

```bash
git clone https://github.com/erykszczesniak/sync-hub.git
cd sync-hub
docker compose up --build
```

First build takes a few minutes (Gradle and npm inside the images). When the hub is healthy, the
scheduler runs the first sync of both feeds within about 15 seconds.

| What | URL | Credentials |
| --- | --- | --- |
| Dashboard | http://localhost:5173 | none (read-only) |
| Hub Swagger UI | http://localhost:8080/swagger-ui.html | control endpoints: `admin` / `change-me` (HTTP Basic) |
| Hub health | http://localhost:8080/actuator/health | – |
| System A Swagger UI | http://localhost:8081/swagger-ui.html | header `X-API-Key: system-a-dev-key` |
| System A GraphiQL | http://localhost:8081/graphiql | same header |
| PostgreSQL | `localhost:5432`, db/user/password `synchub` | – |
| Kafka (from the host) | `localhost:29092` | – |

If 5432 or 5173 are already taken on your machine, override the host ports:
`POSTGRES_PORT=55432 DASHBOARD_PORT=5174 docker compose up --build`. All variables are documented in
[`.env.example`](.env.example) and [`docs/CONFIGURATION.md`](docs/CONFIGURATION.md).

### Running without Docker (H2 path)

No external services needed: the `dev` profile uses embedded H2 and writes change events to the
log instead of Kafka.

```bash
./gradlew :system-a-sim:bootRun        # source on :8081
./gradlew :hub:bootRun                 # hub on :8080, H2 file db in ./h2-data
cd dashboard && npm ci && npm run dev  # dashboard on :5173, /api proxied to the hub
```

## 6. Run the demo scenario

With the stack up:

```bash
./scripts/demo.sh
```

The script walks through the whole story and asserts each outcome (it is also the CI smoke test):

1. **Normal sync** of both feeds: customers over REST, orders over GraphQL.
2. **Re-run with nothing changed**: 0 loaded, nothing duplicated.
3. **An incremental change**: rename a customer in System A, sync, exactly one record loaded. Watch the
   Overview page: the customers card shows the run and the watermark moving.
4. **Schema drift**: System A starts sending `emailAddress` instead of `email` and touches a few
   customers. The run is `PARTIAL`, the records are quarantined, one `FIELD_RENAMED` event is
   raised, the feed turns `DRIFT`. Open **Drift & quarantine** on the dashboard to see the event and
   expand a quarantined record to its raw payload.
5. **The fix**: the rename is rolled back and a backfill of the window re-syncs the records. The
   quarantine closes, the drift event is resolved automatically, MTTR is recorded.
6. **Overview** prints totals, MTTD, MTTR and the number of change events on Kafka.

To do the same by hand, use Swagger UI on both services: `PUT /admin/drift` on System A, then
`POST /api/sync/customers/run` on the hub, and so on. To see the events:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic synchub.changes.customers --from-beginning
```

## 7. Key concepts shown

| Concept | Where to see it |
| --- | --- |
| Incremental sync with watermarks and an overlap window | `sync/WatermarkStore.kt`, `sync/SyncOrchestrator.kt`; the watermark column on the Runs page |
| REST and GraphQL extraction with retry, rate limit and circuit breaker | `source/rest/CustomerRestExtractor.kt`, `source/graphql/OrderGraphQlExtractor.kt`, `resilience4j.*` in `application.yml` |
| Canonical model with explicit validation | `canonical/` |
| Schema mapping across systems | `transform/CustomerSourceMapper.kt`, `sink/SinkMappers.kt` |
| Idempotency, late arrivals, backfill without duplication | `sink/SinkLoader.kt` and `CustomerSinkLoaderTest`; step 2 and 5 of the demo |
| Schema drift: detect, quarantine, resolve | `transform/SchemaDriftDetector.kt`, `docs/SCHEMA-DRIFT.md`; Drift & quarantine page |
| CDC-style change events, at-least-once via an outbox | `events/`, `docs/DELIVERY-SEMANTICS.md`; the Kafka topics |
| Run log, feed health, lag, MTTD / MTTR | `status/StatusService.kt`; the Overview page |
| Strict DTO boundary | Controllers return DTOs only (`status/StatusDtos.kt`), entities never leave the service layer |

## 8. Project structure

```
sync-hub/
├── hub/                                  # Kotlin + Spring Boot: the integration core
│   ├── Dockerfile
│   └── src/main/kotlin/com/erykszczesniak/synchub/
│       ├── canonical/                    # neutral model + explicit validation
│       ├── source/                       # System A clients: REST (customers), GraphQL (orders), resilience
│       ├── transform/                    # source→canonical mappers, schema contracts, drift detection
│       ├── sink/                         # System B records, canonical→B mappers, idempotent loaders
│       ├── sync/                         # feed pipelines, orchestrator, watermarks, quarantine, drift events, scheduler
│       ├── events/                       # change events, transactional outbox, Kafka / log transports
│       ├── status/                       # read-only status API, sync control, remediation endpoints
│       ├── repository/                   # JPA entities and repositories (hub state + System B)
│       ├── common/                       # source exceptions, problem details, fingerprints
│       └── config/                       # security, OpenAPI, scheduling
│   └── src/main/resources/db/migration/  # Flyway: V1 hub state, V2 System B, V3 outbox
├── system-a-sim/                         # Spring Boot source simulator (REST + GraphQL + admin/drift controls)
├── dashboard/                            # React + Vite + TypeScript observability UI
│   └── src/{api,app,components,pages,styles}
├── docs/                                 # CONFIGURATION, SCHEMA-DRIFT, DELIVERY-SEMANTICS
├── scripts/demo.sh                       # the scripted end-to-end scenario (also the CI smoke test)
├── config/detekt/                        # detekt overrides
├── .github/workflows/                    # backend, dashboard, compose smoke
├── docker-compose.yml                    # PostgreSQL + Kafka + System A + hub + dashboard
└── .env.example
```

### Development

```bash
./gradlew ktlintCheck detekt build   # backend: lint, build, unit + Testcontainers tests (Docker required)
cd dashboard && npm run lint && npm run typecheck && npm test && npm run build
```

## 9. Screenshots

_Placeholders; captured from the running demo._

| Overview | Runs | Drift & quarantine |
| --- | --- | --- |
| `docs/screenshots/overview.png` | `docs/screenshots/runs.png` | `docs/screenshots/drift.png` |

## License

MIT
