# short-url — URL Shortener at Scale

A horizontally scalable URL-shortening service built with Spring Boot,
consistent hashing, and Redis. Designed so the cache/storage tier and the
app tier can each scale out independently, without a central bottleneck.

## Architecture

```
                        ┌─────────────┐
        clients ──────► │    nginx    │  (round-robin / least-conn LB)
                        └──────┬──────┘
                   ┌───────────┴───────────┐
                   ▼                       ▼
             ┌───────────┐           ┌───────────┐
             │  app-1     │           │  app-2     │   ...  app-N
             │ (node id 1)│           │ (node id 2)│
             └─────┬──────┘           └─────┬──────┘
                   │                        │
                   └───────────┬────────────┘
                                ▼
                    consistent hash ring
                    (150 virtual nodes / shard)
                                │
              ┌─────────────────┼─────────────────┐
              ▼                 ▼                 ▼
        ┌───────────┐    ┌───────────┐     ┌───────────┐
        │ redis-1   │    │ redis-2   │     │ redis-3   │
        └───────────┘    └───────────┘     └───────────┘
```

**Why consistent hashing, not `hash(key) % N`:** with modulo sharding,
adding or removing a Redis node reshuffles almost the entire keyspace and
invalidates nearly every cached entry at once. The ring only remaps
`~1/N` of keys when a node joins or leaves, so the shard tier can grow
without a mass cache-miss stampede. This is verified in
[`ConsistentHashRingTest`](src/test/java/com/toshif/shorturl/hashing/ConsistentHashRingTest.java):
adding a 4th shard to a 3-shard ring remaps only ~20-25% of existing keys.

**Why Snowflake IDs, not a Redis `INCR` counter:** a single shared counter
becomes a bottleneck and a single point of failure once there's more than
one app instance behind the load balancer. Each app instance is given a
unique `node-id` and mints 64-bit, time-ordered, collision-free IDs
locally — no coordination between instances required. The ID is then
Base62-encoded into the short code.

**Request flow:**
- `POST /api/v1/shorten` — generate a Snowflake ID → Base62-encode it →
  route the resulting code through the consistent hash ring to its shard →
  `SET code -> longUrl` (optionally with a TTL) on that shard.
- `GET /{code}` — route the code to its shard, `GET` the long URL, respond
  `302 Found` with `Location` set. A miss returns `404`.

## Running locally (single shard)

```bash
docker run -d -p 6379:6379 redis:7-alpine
mvn spring-boot:run
```

```bash
curl -X POST localhost:8080/api/v1/shorten \
  -H "Content-Type: application/json" \
  -d '{"longUrl":"https://example.com/some/very/long/path"}'
# -> {"shortUrl":"http://localhost:8080/2Bx9F","shortCode":"2Bx9F", ...}

curl -i localhost:8080/2Bx9F
# -> HTTP/1.1 302 Found
#    Location: https://example.com/some/very/long/path
```

## Running the full scaled stack (3 shards, 2 app nodes, nginx)

```bash
docker compose up --build
curl -X POST localhost:8080/api/v1/shorten -H "Content-Type: application/json" \
  -d '{"longUrl":"https://example.com"}'
```

## Tests

```bash
mvn test
```

Covers: consistent-hash routing stability and distribution, the
minimal-remap property when shards are added/removed, Base62 round-trips,
and Snowflake ID uniqueness/ordering (including across concurrent
"nodes").

## Load testing

Scripts live in `loadtest/` and use [`wrk`](https://github.com/wg/wrk).

```bash
# 1. Bring the stack up
docker compose up --build -d

# 2. Write-path load test (POST /api/v1/shorten)
wrk -t8 -c400 -d30s -s loadtest/shorten_workload.lua http://localhost:8080

# 3. Seed some real codes, then hammer the read/redirect path
./loadtest/seed.sh http://localhost:8080 5000
wrk -t8 -c400 -d30s -s loadtest/redirect_workload.lua http://localhost:8080
```

Both scripts print p50/p90/p99 latency and an error count alongside wrk's
usual requests/sec summary. Scale `-t` (threads) and `-c` (connections) to
your machine, and repeat against `app-1`/`app-2` directly (bypassing nginx)
to isolate load-balancer overhead from app throughput.

**Measured results** (Apple Silicon M-series, 8 cores, Docker Desktop,
full stack: 3 Redis shards + 2 app instances + nginx, all on localhost):

| Path | Command | Requests/sec | Errors | p50 | p99 |
|---|---|---|---|---|---|
| Write (`POST /shorten`) | `wrk -t4 -c100 -d20s` | 4,239.74 | 0 / 85,117 | 19.92ms | 177.66ms |
| Read (`GET /{code}`) | `wrk -t4 -c100 -d20s` | 7,508.58 | 0 / 150,572 | 11.84ms | 58.19ms |
| Read (`GET /{code}`) | `wrk -t8 -c300 -d20s` | **9,275.09** | 0 / 186,223 | 26.97ms | 89.09ms |

**Independent confirmation from GitHub Actions CI** (shared, throttled
2-vCPU runner — a deliberately weaker machine than a dedicated dev
laptop, run automatically by the `load-test` job on every push):

| Path | Requests/sec | Errors | p50 | p99 |
|---|---|---|---|---|
| Write (`POST /shorten`) | 1,691.73 | 0 / 33,900 | 105.14ms | 1007.87ms |
| Read (`GET /{code}`) | 6,116.27 | 0 / 122,737 | 27.73ms | 183.32ms |

Lower throughput than the dedicated-hardware numbers above, as expected
on shared cloud CPU, but zero errors across both runs — confirming the
stack behaves correctly under load on infrastructure nobody hand-tuned
for the test.

The read path (`GET /{code}`) is a single Redis `GET` behind Tomcat and
scales further with more `wrk` threads/connections, since Redis itself
comfortably handles 100k+ ops/sec in memory and the app layer just proxies
that. The write path additionally pays for JSON (de)serialization,
validation, and Snowflake ID generation, so it runs lower.

These numbers are specific to the machine they were run on — CPU cores,
Docker Desktop's VM resource limits, and loopback vs. real network all
matter. Re-run the commands below on your own hardware if you want your
own numbers:

```bash
# 1. Bring the stack up
docker compose up --build -d

# 2. Write-path load test (POST /api/v1/shorten)
wrk -t8 -c300 -d20s -s loadtest/shorten_workload.lua http://localhost:8080

# 3. Seed some real codes, then hammer the read/redirect path
./loadtest/seed.sh http://localhost:8080 2000
wrk -t8 -c300 -d20s -s loadtest/redirect_workload.lua http://localhost:8080
```

A convenient way to get a number reproducibly is via the `load-test` job
in `.github/workflows/ci.yml`, which builds the stack in CI, runs both
scripts, and uploads the output as a build artifact.

## Project layout

```
src/main/java/com/toshif/shorturl/
├── hashing/     ConsistentHashRing, Base62, SnowflakeIdGenerator
├── service/     RedisShardRouter, UrlShortenerService
├── controller/  UrlShortenerController
├── config/      ShardProperties, AppProperties, AppConfig
├── dto/         ShortenRequest, ShortenResponse
└── exception/   ShortCodeNotFoundException, GlobalExceptionHandler
```

## Possible extensions

- Persist mappings to a durable store (Postgres/DynamoDB) with Redis as a
  cache-aside layer in front, for durability beyond Redis's own
  persistence settings.
- Custom vanity codes and per-link click analytics.
- Rate limiting per API key at the nginx or app layer.
- Replace the single-writer-per-shard Jedis pool with Redis Cluster or
  Sentinel for shard-level HA (today, losing a shard loses that shard's
  keys — the ring only helps distribute load, not survive a node dying).
