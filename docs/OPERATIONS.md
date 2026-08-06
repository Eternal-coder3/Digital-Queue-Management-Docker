# SmartQueue operations

## Configuration and secrets

Copy `.env.example` to `.env.local` and replace every placeholder. `.env.local` is ignored by Git. Production secrets must come from the deployment platform's secret manager; never put them in Compose files or `appsettings.json`.

The previously committed Gmail application password has been removed from the current files. Because a Git commit cannot revoke a credential, the Gmail owner must revoke that application password immediately and create a new one only if real email delivery is required. The old value remains in Git history until the repository owner approves a coordinated history rewrite.

## Start and stop the container stack

```powershell
.\scripts\start-docker.ps1
.\scripts\stop-docker.ps1
```

To include Prometheus and Grafana:

```powershell
.\scripts\start-docker.ps1 -Observability
```

The application is available at `http://localhost:8080`. Health is exposed internally on management port 9091, Prometheus on `http://localhost:9090`, and Grafana on `http://localhost:3000` when the observability profile is enabled.

## Backup and restore

Create and verify a PostgreSQL custom-format backup:

```powershell
.\scripts\backup-database.ps1
```

Restore only after making a fresh backup and stopping application traffic:

```powershell
.\scripts\restore-database.ps1 -BackupFile .\backups\smartqueue-YYYYMMDD-HHMMSS.dump -Force
```

Backups are local disaster-recovery artifacts, not a complete production policy. Production must copy encrypted backups off the host, restrict access, monitor failures, and run scheduled restore drills.

## Monitoring and logs

- Liveness: `/actuator/health/liveness`
- Readiness: `/actuator/health/readiness`
- Metrics: `/actuator/prometheus`
- Every backend response carries `X-Correlation-ID`; incoming values are preserved and included in the logging context.
- Production backend console logs use structured ECS JSON.
- The notification service exposes `/health`.

Alert on sustained readiness failures, HTTP 5xx rates, latency, JVM pressure, exhausted database connections, Redis failures, and backup failures.

## Authentication

Access tokens expire after 15 minutes. The browser refresh token is random, stored only as a SHA-256 hash in PostgreSQL, sent in an HttpOnly SameSite cookie, rotated on every refresh, and revoked on logout or password reset. Set `REFRESH_COOKIE_SECURE=true` whenever HTTPS is used.

## Release checklist

1. Run `mvn -pl backend verify`.
2. Run the Playwright suite against a disposable environment.
3. Build the Compose images and wait for all health checks.
4. Back up the database and verify a restore in a non-production environment.
5. Confirm production secrets, HTTPS, allowed origins, log shipping, alerts, and rollback ownership.
