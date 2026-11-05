# Security Policy

Mobile AWS Tools handles AWS credentials and opens shells on cloud
instances, so security reports are taken seriously.

## Reporting a vulnerability

**Do not open a public issue.** Use GitHub's private vulnerability
reporting: go to the repository's **Security** tab and choose
**Report a vulnerability**.

Please include the affected version or commit, steps to reproduce and the
impact you expect. You should get an acknowledgement within 7 days.

## Supported versions

Until version 1.0, only the latest release and `main` receive fixes.

## Scope

In scope: the Android app, the Go bridge, the plugin registry and the build
and release pipeline (for example, leaking credentials or `TokenValue`,
bypassing the biometric lock, connecting to a non-AWS stream endpoint, or
tampering with plugin installation).

Out of scope: vulnerabilities in AWS services or in the upstream Session
Manager plugin; report those to AWS at
https://aws.amazon.com/security/vulnerability-reporting/.

## Handling credentials

- The app only uses temporary credentials (IAM Identity Center).
- Tokens are stored encrypted with Android Keystore; backups are disabled.
- Terminal and credential screens use `FLAG_SECURE`.
