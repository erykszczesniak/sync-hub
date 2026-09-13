# Configuration

All configuration is read from the environment (Spring relaxed binding). Defaults are safe for local
development only.

| Variable | Default | Used by | Purpose |
| --- | --- | --- | --- |
| `HUB_PORT` | `8080` | hub | HTTP port of the hub |
| `SYNCHUB_ADMIN_USERNAME` | `admin` | hub | Operator account for control endpoints (HTTP Basic) |
| `SYNCHUB_ADMIN_PASSWORD` | `change-me` | hub | Operator password |
| `SPRING_PROFILES_ACTIVE` | `dev` | hub | `dev` = embedded H2, no external services; `prod` = PostgreSQL + Kafka |
| `SPRING_DATASOURCE_URL` | – | hub (prod) | JDBC URL of the hub/System B database |
| `SPRING_DATASOURCE_USERNAME` | – | hub (prod) | Database user |
| `SPRING_DATASOURCE_PASSWORD` | – | hub (prod) | Database password |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:29092` | hub | Kafka brokers for change events |
| `SYNCHUB_EVENTS_TRANSPORT` | `log` (dev) / `kafka` (prod) | hub | Where change events go: `kafka` or `log` |
| `SYNCHUB_SYNC_SCHEDULED` | `true` | hub | Run incremental syncs on a schedule |
| `SYNCHUB_SYNC_INTERVAL` | `60s` | hub | Delay between scheduled passes |
| `SYSTEM_A_BASE_URL` | `http://localhost:8081` | hub | Base URL of System A |
| `SYSTEM_A_API_KEY` | `system-a-dev-key` | hub, system-a-sim | API key the hub sends and the simulator expects |
| `SYSTEM_A_PORT` | `8081` | system-a-sim | HTTP port of the simulator |

## Profiles

- `dev` (default): embedded H2, change events logged instead of published. Runs with
  `./gradlew :hub:bootRun` and needs nothing else besides the System A simulator.
- `prod`: PostgreSQL, Flyway migrations, Kafka. This is what `docker compose up` uses.
- `test`: H2 for unit and slice tests; Testcontainers-backed tests declare their own containers.

## URLs (defaults)

| What | URL |
| --- | --- |
| Hub Swagger UI | http://localhost:8080/swagger-ui.html |
| Hub OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Hub health | http://localhost:8080/actuator/health |
| System A Swagger UI | http://localhost:8081/swagger-ui.html |
| System A GraphiQL | http://localhost:8081/graphiql |
