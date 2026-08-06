# Security notes

## Exposed email credential

An email application password existed in commit `7b0b405`. Its removal from `appsettings.json` prevents new deployments from using it but does not remove it from existing clones or Git history.

Required owner actions:

1. Revoke the old application password at the email provider immediately.
2. Review provider sign-in and sending activity.
3. Rotate any related credentials that reused the same secret.
4. If the repository was shared, coordinate a `git filter-repo` history rewrite with every collaborator and remote owner. Do not rewrite shared history without that coordination.

New secrets belong in `.env.local` for isolated local development and in a managed secret store for production. Secret scanning should be enabled in the repository host and CI pipeline.
