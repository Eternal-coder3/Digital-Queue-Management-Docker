# SmartQueue local Windows setup

## Current isolated architecture

SmartQueue runs with project-local tools and dedicated Docker resources:

| Component | Version | Address | Storage |
| --- | --- | --- | --- |
| Spring Boot backend and static frontend | Spring Boot 4.1.0 / Java 21 | http://localhost:8080 | Project process |
| Notification service | .NET 8, mock email mode | http://localhost:5050 | Project process |
| PostgreSQL | 18 | localhost:15432 | Docker volume `smartqueue-local_smartqueue-postgres-data` |
| Redis | 7.4 | localhost:56379 | Dedicated Docker container |
| Maven | 3.9.16 | Not applicable | `tmp/maven` |
| Maven dependency cache | Not applicable | Not applicable | `tmp/m2` |
| .NET runtime | 8.0 | Not applicable | `tmp/dotnet8` |

The system PostgreSQL service on port 5432 and Memurai on port 6379 are not used by this setup.

## Start SmartQueue each day

1. Start Docker Desktop and wait until its engine is running.
2. Open PowerShell in the project folder: `D:\Smart Queue`.
3. Run:

   ```powershell
   .\scripts\start-local.ps1
   ```

4. Open http://localhost:8080/.

Useful URLs:

- Application: http://localhost:8080/
- Backend health: http://localhost:8080/api/v1/health
- Backend Swagger: http://localhost:8080/swagger-ui.html
- Notification Swagger: http://localhost:5050/swagger/index.html

Logs are under `tmp/logs`.

## Stop SmartQueue and preserve database data

From PowerShell in `D:\Smart Queue`, run:

```powershell
.\scripts\stop-local.ps1
```

This stops the application and containers but keeps the PostgreSQL Docker volume.

## Remove the isolated environment completely

First run:

```powershell
.\scripts\remove-local.ps1
```

This deletes only the Docker containers, network, and PostgreSQL volume belonging to the `smartqueue-local` Compose project. Database data in that volume cannot be recovered afterward.

Afterward, delete `D:\Smart Queue\tmp` to remove the project-local Maven installation, .NET runtime, Maven dependency cache, logs, and process metadata.

The source code remains. System PostgreSQL and Memurai are not removed or changed by these scripts.

## Database creation

The Compose configuration creates the database and login:

- Database: `smartqueue`
- Username: `smartqueue`
- Internal development-only password: defined in `compose.local.yml`

Flyway is the only schema creation mechanism. At startup it applies migrations V1 through V19. Hibernate uses `ddl-auto: validate` and does not create tables itself.

The resulting database has 13 application tables plus `flyway_schema_history`. The roles table is seeded with `ADMIN`, `CITIZEN`, and `OFFICER`.

## Known uploaded-project issue

The Java project compiles. Twelve of thirteen backend tests pass. The remaining integration test expects `DELETE /api/v1/users/{id}`, but `UserDirectoryController` does not implement that route. Spring therefore returns HTTP 500 through the global exception handler. This is an application/test mismatch, not a Java, PostgreSQL, or Redis setup failure. Business logic was not changed during local setup.

## Email safety

The notification service runs with `ASPNETCORE_ENVIRONMENT=Development`, which enables mock mode. It does not send real emails. A real-looking Gmail app password was present in the copied `notification-service/appsettings.json`; revoke that credential and replace it with secure configuration before enabling real email.
