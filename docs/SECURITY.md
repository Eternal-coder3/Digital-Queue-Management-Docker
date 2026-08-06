# Security policy

## Reporting a vulnerability

Please do not open a public issue for security vulnerabilities. Contact the repository owner privately.

## Secrets and configuration

- Real credentials must never be committed.
- Copy `.env.example` to `.env.local` for local development.
- `.env.local` is excluded from Git.
- Production secrets should use a managed secret store.
- Revoke and rotate credentials immediately if accidentally exposed.
- Enable secret scanning and dependency alerts on GitHub.

## Supported version

Security updates currently apply to the latest version on the `main` branch.