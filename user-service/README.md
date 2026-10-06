# TicketHub – User Service

Spring Boot 3 microservice for users, roles, authentication (JWT) and authorization (role-based).

## Data model

| Table        | Columns |
|--------------|---------|
| `users`      | id, first_name, last_name, email (unique), phone (unique), password_hash, status (ACTIVE / INACTIVE / BLOCKED), last_login_at, created_at, updated_at, created_by, updated_by |
| `roles`      | id, name (CUSTOMER / ADMIN, unique), description, created_at, updated_at, created_by, updated_by |
| `user_roles` | user_id + role_id (composite primary key), created_at, updated_at, created_by, updated_by |

`User` and `Role` are many-to-many; `UserRole` is the mapping entity.
`created_by` / `updated_by` hold the email of the logged-in user, or `system` when nobody is logged in (registration, startup seeding).

## Run it

1. Install Java 17+ and Maven, and start MySQL.
2. Edit `src/main/resources/application.properties` – set `spring.datasource.username` / `password`.
   The database `tickethub_user_db` is created automatically.
3. Start: `mvn spring-boot:run` (runs on port **8081**).

On first start the roles `CUSTOMER` and `ADMIN` are created, plus a default admin:
`admin@tickethub.com` / `Admin@12345` (change in `application.properties`).

Set a real secret for production: `export JWT_SECRET=<at least 32 characters>`.

## Endpoints

| Method | URL | Who | Purpose |
|--------|-----|-----|---------|
| POST | `/api/auth/register` | public | Register (gets CUSTOMER role) |
| POST | `/api/auth/login` | public | Returns a JWT |
| GET | `/api/users/me` | logged in | My profile |
| PUT | `/api/users/me` | logged in | Update first name, last name, phone |
| PUT | `/api/users/me/password` | logged in | Change password |
| GET | `/api/admin/users` | ADMIN | List users |
| GET | `/api/admin/users/{id}` | ADMIN | One user |
| PATCH | `/api/admin/users/{id}/status` | ADMIN | ACTIVE / INACTIVE / BLOCKED |
| PUT | `/api/admin/users/{id}/roles` | ADMIN | Replace roles, e.g. `{"roles":["ADMIN","CUSTOMER"]}` |
| DELETE | `/api/admin/users/{id}` | ADMIN | Delete user |

Send the token on protected calls: `Authorization: Bearer <token>`.

### Quick try (curl)

```bash
curl -X POST localhost:8081/api/auth/register -H "Content-Type: application/json" \
  -d '{"firstName":"Ravi","lastName":"Kumar","email":"ravi@example.com","phone":"9876543210","password":"Secret@123"}'

curl -X POST localhost:8081/api/auth/login -H "Content-Type: application/json" \
  -d '{"email":"ravi@example.com","password":"Secret@123"}'

curl localhost:8081/api/users/me -H "Authorization: Bearer <token>"
```

## How security works

1. Login: Spring's `AuthenticationManager` checks email + BCrypt password (`CustomUserDetailsService` loads the user). Inactive users get 403, blocked users get 403.
2. A JWT (subject = email, roles, 1 hour expiry) is returned.
3. `JwtAuthFilter` runs on every request, validates the token, reloads the user from the DB (so blocking takes effect immediately) and sets the logged-in user.
4. `SecurityConfig` rules: `/api/auth/**` public, `/api/admin/**` needs `ROLE_ADMIN`, everything else needs a valid token. Admin controller methods also carry `@PreAuthorize`.

## Package layout

`config` (security, auditing, startup seeding) · `controller` · `dto` · `entity` · `exception` · `repository` · `security` (JWT + user details) · `service`

## Tests

`mvn test` runs `UserServiceFlowTest` on an in-memory H2 database (no MySQL needed): register, login, JWT, roles, blocking, auditing.
