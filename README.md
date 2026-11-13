# Task Manager Backend

Spring Boot 3 + MongoDB REST API for a mobile app: JWT login, admin-managed
users, and per-user daily tasks.

- **Java 21**, **Spring Boot 3.5.16**, **Spring Data MongoDB**, **Spring Security**
- Stateless **JWT** authentication (HS256, `jjwt` 0.12)
- **BCrypt** password hashing
- Bean-validation on every request body with consistent JSON errors
- OpenAPI / Swagger UI at `/swagger-ui.html`
- Integration tests that exercise the real HTTP stack against MongoDB

---

## Features

- Admin bootstrapped on first startup (configurable credentials)
- Admin-only user management: create, list/search/filter, fetch, update, delete
  (deleting a user cascades their tasks)
- Every user owns their daily tasks: create, list/filter, today view, fetch one,
  partial update, status change, delete
- Task ownership is enforced server-side: another user's task is reported as
  `404 Not Found` (no existence leak)
- Optional email, roles (`ADMIN` / `USER`), enable/disable accounts
- Pagination on every list endpoint

---

## Project structure

```
src/main/java/com/taskmanager
├── TaskManagerApplication.java
├── config/          SecurityConfig, OpenApiConfig, DataInitializer
├── controller/      AuthController, AdminUserController, TaskController
├── domain/          User, Task, Role, TaskStatus, TaskPriority
├── dto/             request/response records, ApiResponse, ApiError, PageResponse
├── exception/       global handler + typed exceptions
├── repository/      Spring Data repositories + MongoTemplate search impls
├── security/        JwtService, JwtAuthenticationFilter, UserPrincipal, handlers
└── service/         AuthService, UserService, TaskService
src/main/resources/application.yml
src/test/java/com/taskmanager/TaskManagerApiIntegrationTest.java
```

---

## Data model

**users** (`users` collection)

| Field | Type | Notes |
|---|---|---|
| `id` | String | Mongo `_id` |
| `username` | String | unique index, login id, immutable |
| `password` | String | BCrypt hash, never returned by the API |
| `name` | String | full name |
| `phoneNumber` | String | basic detail |
| `email` | String | optional, unique (case-insensitive) when present |
| `roles` | Set<Role> | `ADMIN`, `USER` |
| `enabled` | boolean | disabled users cannot log in |
| `createdAt`, `updatedAt` | Instant | audit fields |

**tasks** (`tasks` collection)

| Field | Type | Notes |
|---|---|---|
| `id` | String | Mongo `_id` |
| `userId` | String | owner (`users._id`) |
| `title` | String | required |
| `description` | String | optional |
| `dueDate` | LocalDate | defaults to today |
| `status` | enum | `PENDING`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED` |
| `priority` | enum | `LOW`, `MEDIUM`, `HIGH` (default `MEDIUM`) |
| `completedAt` | Instant | stamped when status becomes `COMPLETED` |
| `createdAt`, `updatedAt` | Instant | audit fields |

Compound index: `{ userId: 1, dueDate: 1 }`.

---

## Getting started

### Prerequisites

- JDK 21
- Maven 3.9+
- MongoDB — by default the app connects to the configured **MongoDB Atlas**
  cluster; a local MongoDB 7/8 (Docker or native) is only needed to run the tests

### 1. Database

The app runs with the `local` Spring profile by default. That profile reads the
connection string from `src/main/resources/application-local.yml`, which is
**gitignored** and already points at the Atlas cluster (`task_manager` database).

- To use a different database, edit `application-local.yml` or override
  `MONGODB_URI`.
- To use a local MongoDB instead:

```bash
mongod --dbpath ./data/db --port 27017
# or
docker compose up -d
```

```bash
export MONGODB_URI='mongodb://localhost:27017/task_manager'
```

The test suite always uses `localhost:27017/task_manager_test` (override with
`TEST_MONGODB_URI`) and never touches the Atlas database.

### 2. Run the API

```bash
mvn spring-boot:run
```

The API starts on `http://localhost:8080`.

On first startup an admin account is created if the username is free:

```
username: admin
password: Admin@12345
```

Change it immediately (see configuration below). The bootstrap only runs when
that username does not already exist.

### 3. Open the docs

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- Health: <http://localhost:8080/actuator/health>

### 4. Run the tests

Tests expect a local MongoDB on `localhost:27017` and use the
`task_manager_test` database (override with `TEST_MONGODB_URI`). The `test`
profile overrides the default `local` profile, so tests never touch Atlas.

```bash
mvn test
```

---

## Configuration

Everything has a dev-friendly default; override via environment variables.

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP port |
| `SPRING_PROFILES_ACTIVE` | `local` (default profile) | profile selection |
| `MONGODB_URI` | Atlas URI from `application-local.yml` (base fallback: `mongodb://localhost:27017/task_manager`) | Mongo connection |
| `JWT_SECRET` | dev-only value | HS256 signing key, **≥ 32 bytes** |
| `JWT_EXPIRATION_MINUTES` | `1440` | token lifetime (24 h) |
| `ADMIN_USERNAME` | `admin` | bootstrap admin username |
| `ADMIN_PASSWORD` | `Admin@12345` | bootstrap admin password |
| `ADMIN_NAME` | `System Administrator` | bootstrap admin name |
| `ADMIN_PHONE` | `9000000000` | bootstrap admin phone |
| `CORS_ALLOWED_ORIGINS` | `*` | comma-separated origin patterns |

Example:

```bash
export MONGODB_URI='mongodb://localhost:27017/task_manager'
export JWT_SECRET="$(openssl rand -base64 48)"
export ADMIN_PASSWORD='change-me-now'
mvn spring-boot:run
```

---

## API reference

All responses use an envelope:

```json
{
  "success": true,
  "message": "Login successful",
  "data": { },
  "timestamp": "2026-11-13T09:15:00Z"
}
```

Authenticated requests need `Authorization: Bearer <accessToken>`.

### Authentication

| Method | Path | Access | Description |
|---|---|---|---|
| POST | `/api/auth/login` | public | username/password → JWT |
| GET | `/api/auth/me` | authenticated | current profile |

`POST /api/auth/login`

```json
{ "username": "john.doe", "password": "Password@123" }
```

```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "<jwt-token>",
    "tokenType": "Bearer",
    "expiresInSeconds": 86400,
    "user": {
      "id": "6730f...",
      "username": "john.doe",
      "name": "John Doe",
      "phoneNumber": "9876543210",
      "email": "john.doe@example.com",
      "roles": ["USER"],
      "enabled": true,
      "createdAt": "...",
      "updatedAt": "..."
    }
  },
  "timestamp": "..."
}
```

### Admin – users (`ADMIN` role required)

| Method | Path | Description |
|---|---|---|
| POST | `/api/admin/users` | create a user (roles default to `USER`) |
| GET | `/api/admin/users?search=&role=&enabled=&page=&size=` | list/search users |
| GET | `/api/admin/users/{id}` | fetch a user |
| PUT | `/api/admin/users/{id}` | partial update (name, phone, email, password, roles, enabled) |
| DELETE | `/api/admin/users/{id}` | delete user + their tasks |
| GET | `/api/admin/users/{id}/tasks?date=&from=&to=&status=&priority=&search=` | fetch that user's tasks |

`POST /api/admin/users`

```json
{
  "username": "john.doe",
  "password": "Password@123",
  "name": "John Doe",
  "phoneNumber": "9876543210",
  "email": "john.doe@example.com",
  "roles": ["USER"]
}
```

`PUT /api/admin/users/{id}` — send only what changes:

```json
{ "enabled": false }
```

### Tasks (authenticated user; always scoped to the caller)

| Method | Path | Description |
|---|---|---|
| POST | `/api/tasks` | create a daily task |
| GET | `/api/tasks?date=&from=&to=&status=&priority=&search=&page=&size=` | list my tasks |
| GET | `/api/tasks/today` | my tasks for today |
| GET | `/api/tasks/{id}` | fetch one of my tasks |
| PUT | `/api/tasks/{id}` | partial update |
| PATCH | `/api/tasks/{id}/status` | change status only |
| DELETE | `/api/tasks/{id}` | delete |

`POST /api/tasks`

```json
{
  "title": "Morning workout",
  "description": "30 minutes cardio",
  "dueDate": "2026-11-13",
  "priority": "HIGH"
}
```

`PATCH /api/tasks/{id}/status`

```json
{ "status": "COMPLETED" }
```

Filters: `date` (single day, `YYYY-MM-DD`), `from`/`to` (inclusive range),
`status`, `priority`, `search` (title/description substring),
`page`/`size`/`sort` (e.g. `sort=dueDate,asc`).

### Quick curl walkthrough

```bash
BASE=http://localhost:8080

# 1. admin login
ADMIN_TOKEN=$(curl -s -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Admin@12345"}' | jq -r .data.accessToken)

# 2. admin creates a user
USER_ID=$(curl -s -X POST "$BASE/api/admin/users" \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"username":"john.doe","password":"Password@123","name":"John Doe","phoneNumber":"9876543210"}' \
  | jq -r .data.id)

# 3. user logs in
USER_TOKEN=$(curl -s -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"john.doe","password":"Password@123"}' | jq -r .data.accessToken)

# 4. user creates a task
curl -s -X POST "$BASE/api/tasks" \
  -H "Authorization: Bearer $USER_TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"Morning workout","description":"30 minutes cardio","priority":"HIGH"}'

# 5. user lists tasks / today
curl -s "$BASE/api/tasks?status=PENDING" -H "Authorization: Bearer $USER_TOKEN"
curl -s "$BASE/api/tasks/today" -H "Authorization: Bearer $USER_TOKEN"

# 6. admin fetches that user's tasks
curl -s "$BASE/api/admin/users/$USER_ID/tasks" -H "Authorization: Bearer $ADMIN_TOKEN"
```

Additional ready-to-run requests are in [`requests.http`](requests.http).

---

## Authentication details

- Login authenticates via Spring Security's `AuthenticationManager` against
  MongoDB-backed `UserDetailsService`.
- The JWT carries `sub` (username), `uid` (user id) and `roles`.
- `JwtAuthenticationFilter` validates signature + expiry and reloads the user on
  every request, so disabling/deleting a user takes effect immediately.
- Disabled accounts receive `403` on login; bad credentials receive `401`.
- Passwords are BCrypt-hashed; the hash never appears in any response.

---

## Error format

```json
{
  "timestamp": "2026-11-13T09:15:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/tasks",
  "fieldErrors": {
    "title": "Title is required"
  }
}
```

| Status | When |
|---|---|
| 400 | validation failure, malformed body, bad date range, self-delete |
| 401 | missing/invalid/expired token, bad credentials |
| 403 | authenticated but not allowed (non-admin on `/api/admin/**`, disabled account) |
| 404 | unknown id, or another user's task |
| 409 | duplicate username/email |
| 500 | unexpected server error (details are logged, not leaked) |

---

## Tests

`src/test/java/com/taskmanager/TaskManagerApiIntegrationTest.java` boots the full
application and covers 17 scenarios, including:

- login success/failure, missing/garbage token
- admin create/list/search/fetch user, duplicate username
- field-level validation errors
- non-admin blocked from admin APIs
- disabled user cannot log in
- self-delete guard and task cascade on user delete
- full task lifecycle (create, list, date/range filters, fetch, status,
  update, delete)
- cross-user task isolation (404) and admin access to a user's tasks
- default due date/priority and `/today`

---

## Production checklist

1. Set a strong `JWT_SECRET` (≥ 32 bytes) and a non-default `ADMIN_PASSWORD`.
2. Restrict `CORS_ALLOWED_ORIGINS` to your real clients.
3. Run MongoDB as a replica set if you need multi-document transactions.
4. Put the API behind TLS.
5. Consider disabling SpringDoc in production
   (`springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false`).
6. Add rate limiting on `/api/auth/login` at the gateway.
7. Keep `application-local.yml` and `.env` out of version control. Rotate the
   Atlas database password if it has ever been pasted into chat, logs, or a commit.
8. Use a dedicated Atlas database user with least privilege, and restrict Atlas
   network access to your server IPs (or a Private Endpoint).
9. Set `SPRING_PROFILES_ACTIVE` explicitly in production instead of relying on the
   `local` default profile.
