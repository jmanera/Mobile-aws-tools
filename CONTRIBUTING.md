# Contributing

Thanks for your interest in Mobile AWS Tools. The project is in an early
phase, so the best way to help right now is to try the spike, open issues
and review pull requests.

## Ground rules

- **English everywhere:** code, comments, commit messages, issues, pull
  requests and documentation.
- **Open source only:** new dependencies must use an OSI-approved license
  compatible with Apache-2.0 (Apache-2.0, MIT, BSD, ISC, MPL-2.0 for
  unmodified files). Strong copyleft licenses (GPL, AGPL) are not accepted.
  State the license of any new dependency in the pull request.
- **No secrets in logs:** never log access keys, session tokens or
  `TokenValue`. The bridge redacts known secrets; do not bypass it.
- By contributing you agree that your contribution is licensed under
  Apache-2.0, as stated in section 5 of the [LICENSE](LICENSE).

## Workflow

1. Open or pick an issue; for larger changes, discuss the approach first.
2. Create a branch from `main` and keep commits small and focused.
3. Write commit messages in the imperative mood ("Add resize support").
4. Make sure `cd bridge && go vet ./... && go test ./...` and
   `cd android && ./gradlew assembleDebug` pass.
5. Open a pull request using the template and link the issue.

Significant technical decisions are recorded as ADRs in
[`docs/adr/`](docs/adr/). If your change makes or reverses such a decision,
add or update an ADR.

## Development setup

See [Building](README.md#building) in the README.

## Code of Conduct

This project follows the [Contributor Covenant](CODE_OF_CONDUCT.md).
