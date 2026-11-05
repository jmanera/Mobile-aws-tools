# 0001. Reuse the official Session Manager plugin through a Go bridge

- Status: Accepted (pending validation on a device against a real instance)
- Date: 2026-11-05
- Phase: 0 (spike)

## Context

An interactive Session Manager session needs two steps:

1. `ssm:StartSession`, a normal AWS API call that returns `SessionId`,
   `StreamUrl` and `TokenValue`.
2. A WebSocket to `ssmmessages` speaking a binary protocol with the SSM
   Agent: handshake, session type negotiation, optional KMS encryption,
   sequence numbers, acknowledgements, retransmission and resume.

Step 2 is implemented by AWS in
[session-manager-plugin](https://github.com/aws/session-manager-plugin)
(Go, Apache-2.0). It ships as a command-line tool invoked by the AWS CLI;
it does not document a library API. The alternative is to reimplement the
protocol in Kotlin.

## Findings

Read at commit `6430675a44032a551be1937fb27a2d3fdfeb27f7` (plugin 1.3.0.0,
2026-10-09).

**The protocol layer is reusable as is.** `src/datachannel` and
`src/communicator` contain the whole protocol and do not depend on a TTY.
They take a logger interface (`log.T`) and callbacks, so they can be driven
programmatically.

**The session layer is not.** `src/sessionmanagerplugin/session` and
`shellsession` are glued to the CLI:

| Coupling | Where | Why it matters on Android |
|----------|-------|---------------------------|
| `os.Exit(0)` on session end | `Session.Stop`, `ShellSession.Stop`, `ResumeSessionHandler` | Would kill the whole app process |
| Reads `os.Stdin`, writes `os.Stdout` | `handleKeyboardInput`, `DisplayMode` | No stdin/stdout in an app |
| Runs `stty` | `disableEchoAndInputBuffering` | No TTY |
| Polls terminal size every 500 ms | `handleTerminalResize` | Size comes from the UI instead |
| OS signals for Ctrl+C / Ctrl+Z | `handleControlSignals` | Sent as bytes from the extra-keys row |
| Credentials from the default config chain | `sdkutil`, `NewKMSService`, `GetResumeSessionParams` | No `~/.aws` on the phone |

The data channel itself still prints three messages to `os.Stdout`
(handshake customer message, channel closed). On Android those go nowhere
and are harmless.

**Packaging needs two workarounds.**

- The plugin has no `go.mod`; it builds in GOPATH mode with a `vendor/`
  tree. Go modules synthesize a module for it, but resolve
  `github.com/twinj/uuid` to v1.0.0, whose API differs from the vendored
  copy (`uuid.CleanHyphen` missing, `UUID` no longer nilable). The upstream
  `twinj/uuid` repository is gone from GitHub. The vendored copy (MIT) is
  kept in `bridge/third_party/twinj-uuid` and wired with a `replace`.
- Other dependencies resolve to current versions (AWS SDK for Go v2,
  gorilla/websocket) and compile without changes.

## Decision

Reuse the plugin's `datachannel` package, and replace its session layer
with our own package `ssmbridge`, compiled to an AAR with
`gomobile bind`.

`ssmbridge` reproduces `Session.OpenDataChannel`, `ShellSession` and
`ResumeSessionHandler` without process-wide side effects:

- `Connect(cfg, listener, logSink)` attaches to a session started elsewhere;
  `StartShell` also calls `StartSession` (spike only).
- `Session.Write`, `Resize`, `Close`; output and state changes arrive on a
  `Listener` interface implemented in Kotlin.
- The plugin's stop handler is replaced by a callback that closes the
  channel and reports `StateClosed`; nothing calls `os.Exit`.
- `ResumeSession` and `TerminateSession` use an SSM client built with the
  static temporary credentials passed in, not the default chain.
- Writes and resizes are serialized with a mutex: the plugin's
  `SendInputDataMessage` increments the sequence number without locking.
- A redacting logger implements `log.T` and strips `TokenValue`, the secret
  key and the session token before anything reaches Logcat.
- `StreamUrl` must be `wss://ssmmessages*` on an AWS domain.

The public API uses only types gomobile supports. Go `int` maps to Java
`long`.

## Known limitations

- **KMS-encrypted sessions read credentials from environment variables.**
  The plugin builds its KMS client from the default config chain, so the
  bridge sets `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` and
  `AWS_SESSION_TOKEN` in the app process. Consequences: one set of
  credentials per process (fine for the MVP, which has one session) and
  credentials live in the process environment, which other processes of
  the same UID could read. Fix before phase 4: propose an upstream change
  to inject the KMS client, or carry a minimal patch.
- `src/datachannel` pulls `testify` into the binary through the plugin's
  mocks package. Harmless, adds size.
- AAR size: 8.7 MB for arm64 + x86_64 with `-ldflags="-s -w"`.

## Validation

| Check | Result |
|-------|--------|
| Bridge compiles for `android/arm64` and `android/amd64` | Done |
| Unit tests (redaction, URL validation, config) | Pass |
| AAR imported in a Compose app, `assembleDebug` | Done |
| Desktop harness reaches `ssm:StartSession` | Done (fake credentials, 403 as expected) |
| Shell on a real EC2 instance from the desktop harness | **Pending** |
| Shell on a real EC2 instance from the phone | **Pending**: the phase 0 acceptance criterion |

If the pending checks fail in a way that needs deep changes to the
plugin, fall back to reimplementing the protocol in Kotlin (plan B), reading
the plugin's code as the reference.

## Consequences

- Protocol fixes from AWS arrive by bumping the pinned commit; a
  regression test against a real instance is needed before each bump.
- The app ships native libraries per ABI, which increases APK size.
- Port forwarding (phase 6, RDS) can reuse the same data channel with the
  `Port` session type.
