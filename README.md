# Lost & Found Service

REST API for a lost & found office: an admin uploads a PDF of found items, users claim them (partial
quantities allowed), and the admin sees who claimed what.

Java 25 · Spring Boot 4.1 · PostgreSQL + Flyway · PDFBox · Spring Security (Azure AD JWT) · Docker

## Run locally

```bash
cp .env.example .env        # set ADMIN_PASSWORD and USER_PASSWORD
mvn -DskipTests package
docker compose up --build   # app on :8080, Postgres on :5432
```

To run the app from IntelliJ or Maven instead, start only the database (`docker compose up db`, which
still needs `.env`) and use the `local` profile:

```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run   # IntelliJ: Active profiles = local
```

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- Health: http://localhost:8080/actuator/health

## Configuration

| Env var                                                        | When         | Purpose                                              |
|----------------------------------------------------------------|--------------|------------------------------------------------------|
| `AZURE_TENANT_ID`                                              | production   | Entra ID tenant that issues the tokens               |
| `AZURE_API_AUDIENCE`                                           | production   | Application ID URI of the API, e.g. `api://lost-found` |
| `DB_HOST` `DB_PORT` `DB_NAME` `DB_USERNAME` `DB_PASSWORD`      | always       | PostgreSQL (defaults: `localhost:5432/lostfound`)    |
| `SPRING_PROFILES_ACTIVE=local`                                 | local        | HTTP Basic instead of JWT                            |
| `ADMIN_PASSWORD` `USER_PASSWORD`                               | local        | Passwords of the local users `admin` and `user`      |

The app refuses to start if a required value is missing.

## Authentication

- **Production (default):** bearer JWT from Azure AD. Signature, expiry, issuer and audience are checked.
  - The user id is the `oid` claim. A token without `oid` gets `401`.
  - Roles come from the `roles` claim: App Roles `ADMIN` and `USER` in the app registration.
- **`local` profile:** HTTP Basic with users `admin` (ADMIN) and `user` (USER). Same authorization rules.
  Only for a developer machine.

## API

Base path: `/api/v1`

| Method | Path                       | Role  | Description                                         |
|--------|----------------------------|-------|-----------------------------------------------------|
| POST   | `/admin/lost-items/import` | ADMIN | Upload a PDF of lost items                          |
| GET    | `/lost-items`              | any   | Page of lost items with remaining quantity          |
| GET    | `/lost-items/{id}`         | any   | One lost item                                       |
| POST   | `/lost-items/{id}/claims`  | USER  | Claim `{"quantity": n}` under the caller's identity |
| GET    | `/admin/lost-items/claims` | ADMIN | Page of lost items with their claimants             |

List endpoints take `page` (from 0) and `size` (1–100, default 20) and return
`{ "content": [...], "page", "size", "totalElements", "totalPages" }`, ordered by id.

Example with the `local` profile:

```bash
set -a; . ./.env; set +a
curl -u "admin:$ADMIN_PASSWORD" -F "file=@lost-items.pdf" localhost:8080/api/v1/admin/lost-items/import
curl -u "user:$USER_PASSWORD" localhost:8080/api/v1/lost-items
curl -u "user:$USER_PASSWORD" -H "Content-Type: application/json" -d '{"quantity": 1}' \
  localhost:8080/api/v1/lost-items/1/claims
curl -u "admin:$ADMIN_PASSWORD" localhost:8080/api/v1/admin/lost-items/claims
```

PDF format — one record per item, each starting with `ItemName`:

```
ItemName: Laptop
Quantity: 1
Place: Taxi
```

If any record is malformed, the whole file is rejected with `400`.

## Design notes

- **Layers:** `web` (controllers, DTOs, error mapping) → `service` → `repository` → `domain`. Entities never leave the service layer.
- **File parsing** is a strategy (`LostItemFileParser`): a new format is a new bean.
- **Atomic claims:** one conditional `UPDATE ... WHERE quantity - claimed_quantity >= :requested`, so concurrent claims
  never over-claim (`ClaimServiceConcurrencyTest`). Database check constraints back this up.
- **Claim identity** comes only from the authenticated caller, never from the request body.
- **Schema** is managed by Flyway (`src/main/resources/db/migration`); Hibernate only validates it.
- **Logging:** every request gets an `X-Request-Id` (taken from the request or generated). It is added to every log
  line and to the response, and each request produces one access-log line.
- **User service:** `UserDirectoryClient` is the seam for the external user service; `MockUserDirectoryClient` returns `User-<id>`.
- **Docker image** only packages the jar built by CI (`target/lost-found-service-*.jar`).

## Tests

`mvn test` — 75 tests: unit, `@WebMvcTest`, `@DataJpaTest`, a concurrency test, and end-to-end tests with RS256
tokens signed by a key generated per run. Tests use H2, and the Flyway migrations run on it too.

## Not implemented yet

- **HTTPS:** terminate TLS at the ingress or load balancer (or `server.ssl.*` with a certificate from a secret store),
  add HSTS, and set `server.forward-headers-strategy` behind a proxy.
- **`application-prod.yml`:** no database defaults, Swagger UI and api-docs disabled, structured JSON logs
  (`logging.structured.format.console`), connection pool sizing, graceful shutdown, minimal actuator exposure.
- **Testcontainers:** run repository and end-to-end tests against real PostgreSQL instead of H2
  (`spring-boot-testcontainers` + `@ServiceConnection`).
- **CI/CD pipeline:** build and test, dependency and secret scanning, SonarQube, build and push the image tagged with
  the project version, deploy.
- **Monitoring:** Micrometer with a Prometheus registry (`/actuator/prometheus`), business metrics (imports, claims,
  rejected claims), tracing (OpenTelemetry), Grafana dashboards and alerts.
- **Also:** rate limiting, and moving the database credentials out of `docker-compose.yml`.
