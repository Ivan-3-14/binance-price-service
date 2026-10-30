# Binance Price Collector Service

A Spring Boot service that periodically fetches the average price of a trading pair from Binance, 
persists it to a PostgreSQL database, and exposes it via a REST API.

## Tech Stack

*   **Java 17** / **Spring Boot 3.5.16**
*   **Spring Data JPA** / Hibernate
*   **PostgreSQL** / **Liquibase** (database migrations)
*   **Docker & Docker Compose**
*   **JUnit 5**, **Mockito** (unit tests), **Testcontainers** & **WireMock** (integration tests)
*   **GitHub Actions** (CI)

## Features

*   Scheduled fetching from Binance API (configurable interval).
*   Graceful handling of Binance rate limits (HTTP 429) and network errors.
*   Four REST endpoints for retrieving data:
    *   `GET /api/v1/prices/latest` — most recent snapshot.
    *   `GET /api/v1/prices/average` — average price for a date range.
    *   `GET /api/v1/prices` — paginated history for a date range (sorted by fetchedAt DESC).
    *   `GET /api/v1/prices/trend` — calculated trend (UPWARD/DOWNWARD/FLAT) for a date range.
*   Standardized error responses using RFC 7807 (`ProblemDetail`).

## How to run

### Using Docker Compose (Recommended)

The easiest way to start the application and the database together:

```bash
docker compose up --build
```

 The API will be available at http://localhost:8080. The scheduler will start fetching data immediately, with a
 default interval of 10 seconds (configured in docker-compose.yml).

## Environment Variables

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `BINANCE_BASE_URL` | `https://api.binance.com` | Binance API URL |
| `BINANCE_SYMBOL` | `BNBUSDT` | Trading pair to fetch |
| `BINANCE_POLL_INTERVAL` | `PT30S` | Polling interval (ISO-8601 format) |
| `BINANCE_CONNECT_TIMEOUT` | `3s` | HTTP connection timeout |
| `BINANCE_READ_TIMEOUT` | `5s` | HTTP read timeout |
| `DB_URL` | `jdbc:postgresql://localhost:5432/binance` | PostgreSQL JDBC URL |
| `DB_USER` | `binance` | Database username |
| `DB_PASSWORD` | `binance` | Database password |
| `BINANCE_DEFAULT_RETRY_AFTER` | `30s` | Fallback delay if Retry-After is missing |

## API Examples

### Get latest price

```http
GET http://localhost:8080/api/v1/prices/latest
```

### Get average price for a period

```http
GET http://localhost:8080/api/v1/prices/average?from=2026-10-01T00:00:00Z&to=2026-10-31T23:59:59Z
```

### Get paginated history

```http
GET http://localhost:8080/api/v1/prices?from=2026-10-01T00:00:00Z&to=2026-10-31T23:59:59Z&page=0&size=20
```

### Get trend

```http
GET http://localhost:8080/api/v1/prices/trend?from=2026-10-01T00:00:00Z&to=2026-10-31T23:59:59Z
```


## Design Decisions & Trade-offs

*   **RestClient with explicit timeouts:** The scenario is synchronous. `RestClient` is the modern synchronous HTTP 
client in Spring Boot 3+. Because `RestClient.builder()` bypasses some autoconfiguration, timeouts are explicitly set
using `JdkClientHttpRequestFactory` to guarantee the scheduler thread cannot hang indefinitely.

*   **BigDecimal for money:** Ensures precision for financial data. `double` causes rounding errors, which is
unacceptable for cryptocurrency prices.

*   **TaskScheduler for dynamic polling:** Allows rescheduling the next poll based on Retry-After header, unlike
@Scheduled(fixedDelay) which has a fixed interval.

*   **No @Transactional on fetchAndSave:** Holding a DB transaction open during a network call exhausts the connection 
pool. `@Transactional` is used only for reading. Saving is done via save(), which opens a short transaction.

*   **Trend calculation:** Compares the average of the first and second halves of the period. A 0.1% threshold is used
to define a `FLAT` trend.

*   **DB Schema Validation:** `spring.jpa.hibernate.ddl-auto=validate` is used. Hibernate only validates the schema; 
Liquibase manages it.

## Known Limitations

*   **Rate Limit Backoff (Retry-After):** The service dynamically reschedules the next poll using TaskScheduler based on
the Retry-After header from Binance. This ensures the service respects Binance's rate limits without blocking the
scheduler thread.

*   **Idempotency:** The service does not deduplicate records. Binance closeTime changes every 5 minutes, but polling
happens every 10 seconds. Attempting to deduplicate by closeTime would drop valid observations. Therefore, every poll
is treated as a new observation.

*   **Scheduler disabled in tests:** `@Profile("!test")` is used on the scheduler to prevent background tasks from 
interfering with integration tests. The scheduler's delegation logic is covered by a dedicated unit test 
(`PriceSchedulerTest`).

* **Error Handling Policy:** The service catches Binance-related exceptions (RestClientException, HTTP errors) and 
continues. Unexpected runtime exceptions (e.g., bugs) are propagated to the scheduler, which logs them and reschedules.
This ensures the schedule is not stopped by unexpected errors.

## Testing

*   **Unit tests:** Cover business logic (trend calculation, validation, error handling) using Mockito.
*   **Integration tests:** Use **Testcontainers** to spin up a real PostgreSQL instance and **WireMock** to mock the
Binance API. This verifies the full data flow from HTTP request to database persistence.

To run tests:

```bash
./gradlew test
```