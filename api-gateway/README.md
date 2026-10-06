# api-gateway

One address for all clients: **http://localhost:8090**. Built on Spring Cloud Gateway (reactive) with the same Spring Boot / Spring Cloud versions as booking-service.

## What happens to a request

```
client ─▶ 1 RequestIdGlobalFilter   adds X-Request-Id, logs "GET /bookings/5 -> 200 (35 ms) requestId=… ip=…"
       ─▶ 2 RateLimitGlobalFilter   per client IP; login/register 10/min, bookings 60/min, rest 300/min  -> 429
       ─▶ 3 JwtAuthGlobalFilter     bad path -> 400 | no/forged/expired token -> 401 | wrong role -> 403
                                    sets X-User-Id / X-User-Email / X-User-Roles from the verified token
       ─▶ 4 route + CircuitBreaker  lb://service-name (Eureka) or a direct URL; service down/slow -> 503 JSON fallback
```

Errors the gateway itself produces always look like
`{"timestamp":…,"status":401,"error":"Unauthorized","message":"…","path":"/bookings/1","requestId":"…"}`.

## Files
```
src/main/resources/application.yml   routes, who-may-call-what rules, rate limits, CORS, timeouts  <- the file you edit most
config/GatewayProperties             typed view of "gateway.*" settings
config/ResilienceConfig              circuit-breaker settings (incl. the 65 s time limit, see below)
security/AccessRuleMatcher           (method, path) -> PUBLIC / AUTHENTICATED / ADMIN / DENY
security/JwtAuthenticator            verifies user-service tokens (same secret, claims userId + roles)
security/PathSanitizer               refuses ../  //  ;  %2e  %2f  paths
ratelimit/InMemoryRateLimiter        fixed-window counter
filter/*                             the three global filters above
web/FallbackController               503 answer when a circuit is open
web/GatewayResponses                 the JSON error shape
```

## Settings you may want to change (all in application.yml)
| What | Where |
|---|---|
| Add/rename a route | `spring.cloud.gateway.routes` |
| Who may call a URL | `gateway.security.rules` (first match wins; `PUBLIC`, `AUTHENTICATED`, `ADMIN`, `DENY`) |
| Rate limits | `gateway.rate-limit` |
| Frontend origins allowed by CORS | env `CORS_ALLOWED_ORIGINS` (comma separated) |
| Skip Eureka for one service | env `BOOKING_SERVICE_URI=http://localhost:8083` (also `USER_`, `PAYMENT_`, `NOTIFICATION_`, `EVENT_SERVICE_URI`) |
| Booking timeout | route `booking-service` → `metadata.response-timeout` (60 s) |
| JWT secret | env `JWT_SECRET` – identical on user-service, booking-service and the gateway |

Adding a new service later: add a route, add its URLs to `gateway.security.rules`, add the Eureka client to that service. Nothing else.

## Tests
`./mvnw test` – unit tests for the rules matcher, JWT checking, filter behaviour, rate limiter and path checks, plus one test that starts the gateway
(without Eureka) and checks 401/404 behaviour.
Manual: `scripts\smoke-test.bat`, `scripts\rate-limit-test.bat`, `postman\TicketHub-Gateway.postman_collection.json`.

## Troubleshooting
| Symptom | Cause / fix |
|---|---|
| `401` on every call although you just logged in | Different `JWT_SECRET`, or the token was issued by an older user-service (no `userId` claim) – restart user-service and log in again. Run the gateway with `logging.level.com.tickethub.gateway=DEBUG` to see the reason. |
| `503` with "The … is temporarily unavailable" | That service is not running or not in Eureka (check http://localhost:8761), or the circuit is open after many failures (closes again after 15 s). |
| `503` for `/api/events` | ticket-hub not running on 8080 (or set `EVENT_SERVICE_URI`). |
| `504 Gateway Timeout` | The service took longer than 30 s (booking route: 60 s). |
| Service shows in Eureka but the gateway gets "Connection refused" | Eureka registered an address the gateway cannot reach. Keep `eureka.instance.hostname=localhost` and `prefer-ip-address=false` on a laptop. |
| Service missing in Eureka | Eureka client not added (pom), `spring.application.name` different, wrong `EUREKA_URL`, or registry not started yet (clients retry; wait ~10 s). |
| `429 Too many requests` while testing | Limits are per IP per minute. Start with `RATE_LIMIT_ENABLED=false` while developing. |
| Browser CORS error | Add your frontend address to `CORS_ALLOWED_ORIGINS`. |
| `400 Invalid request path` | The URL contained `..`, `//`, `;` or an encoded `%2e/%2f`. Not used by TicketHub, refused on purpose. |
| Gateway fails at start: "jwt-secret … at least 32 characters" | `JWT_SECRET` is set but too short. |

## Limits you should know (and what to do before production)
* **Resilience4j's own time limit defaults to 1 second** – too short for booking (payment/Razorpay call). `ResilienceConfig` sets 65 s; keep it higher than the HTTP timeouts.
* **Rate limiting is in memory, per gateway instance and per TCP peer IP.** Fine for one gateway. Several gateway instances, or a reverse proxy in front (everybody then has the proxy's IP), need Redis-based limiting and a trusted `X-Forwarded-For` setup.
* **The gateway trusts the signature and expiry of the token only.** A user blocked or demoted in user-service stays valid until the token expires (≤ 1 hour), the same limit booking-service has.
* **HTTPS:** terminate TLS in front of or on the gateway before real use; tokens travel in headers.
* **Actuator:** only `health,info` are exposed. Never expose the `gateway`/`env` actuator endpoints on a reachable machine (known abuse path).
* **Versions:** Spring Boot 3.3 / Spring Cloud 2023.0.3 are used because they match booking-service, but both are out of open-source support. Upgrade all services together when you can.
* The booking test page route (`/razorpay-checkout-test.html`) and its rule are for development – delete both.
