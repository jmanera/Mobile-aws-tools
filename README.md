# Mobile AWS Tools

An open-source Android client for **AWS Systems Manager Session Manager**:
sign in with IAM Identity Center, pick an EC2 instance and get an interactive
shell from your phone, with no SSH keys and no inbound ports.

> **Status: phase 0 (spike).** The repository currently proves that the
> official Session Manager plugin can run inside an Android app. It is not
> usable as a daily tool yet. See the [roadmap](#roadmap).

## How it works

```
┌──────────── Android app ────────────┐
│ Kotlin / Compose UI                 │      ssm:StartSession
│   │  SessionId, StreamUrl, Token    │ ───────────────────────► AWS SSM
│   ▼                                 │
│ ssmbridge (Go, gomobile AAR)        │      WebSocket (wss)
│   └─ official plugin data channel   │ ───────────────────────► ssmmessages ──► SSM Agent on EC2
└─────────────────────────────────────┘
```

The app only obtains the session from AWS. The protocol itself (handshake,
acknowledgements, retransmission, KMS encryption) is handled by the data
channel of the official [session-manager-plugin](https://github.com/aws/session-manager-plugin),
compiled for Android with [gomobile](https://pkg.go.dev/golang.org/x/mobile/cmd/gomobile).
Why, and what had to change, is recorded in
[ADR 0001](docs/adr/0001-reuse-official-plugin.md).

## Repository layout

| Path | Contents |
|------|----------|
| `bridge/` | Go module `ssmbridge`: gomobile-friendly wrapper around the plugin's data channel |
| `bridge/cmd/ssmshell` | Desktop harness to test the bridge against a real instance |
| `android/app` | The app (Jetpack Compose); debug builds also ship the phase 0 spike |
| `android/plugin-api` | Contract for target plugins (pure Kotlin) |
| `android/plugins/ec2-shell` | Built-in plugin: EC2 shell through Session Manager |
| `android/core-aws` | SSO sign-in, token storage and role credentials |
| `android/terminal` | Terminal emulator view (phase 3) |
| `android/bridge` | Exposes the gomobile AAR to the other modules |
| `docs/adr/` | Architecture decision records |

## Building

Requirements: Go 1.26+, JDK 17, Android SDK (platform 35) with an NDK,
and gomobile:

```sh
go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/mobile/cmd/gobind@latest
gomobile init
```

Build the bridge and the app:

```sh
export ANDROID_HOME=~/Android/Sdk
bridge/build-aar.sh                 # writes android/bridge/libs/ssmbridge.aar
cd android && ./gradlew assembleDebug
```

Run the tests:

```sh
cd bridge && go test ./...
cd android && ./gradlew testDebugUnitTest :plugin-api:test
```

## Trying the spike

1. Launch an EC2 instance with the `AmazonSSMManagedInstanceCore` policy on its
   instance role and check it is listed under *Systems Manager → Fleet Manager*.
2. Get temporary credentials allowed to call `ssm:StartSession`,
   `ssm:ResumeSession` and `ssm:TerminateSession` on that instance:

   ```sh
   aws configure export-credentials --format process
   ```

3. **Desktop first:** test the bridge without a phone:

   ```sh
   eval "$(aws configure export-credentials --format env)"
   cd bridge && go run ./cmd/ssmshell -region eu-west-1 -target i-0123456789abcdef0
   ```

4. **On Android:** install `android/app/build/outputs/apk/debug/app-debug.apk`,
   open *SSM Spike (debug)* from the launcher, enter the region and instance
   id, then hand over temporary credentials
   with the device connected over USB (typing them with `adb shell input text`
   truncates the token):

   ```sh
   aws sts get-session-token --duration-seconds 3600 | adb shell "run-as dev.mobileawstools.debug sh -c 'umask 077; mkdir -p files; cat > files/credentials.json'"
   ```

   Tap *Load credentials file* (the app reads the file and deletes it), then
   *Start session*.

The spike shows output as plain text; a real terminal emulator arrives in
phase 3. Never use long-lived IAM user keys with it.

## Roadmap

| Phase | Goal |
|-------|------|
| 0 | Spike: the official plugin works inside Android |
| 1 | App skeleton, CI, `plugin-api`, SSO login (device authorization) |
| 2 | Account, role and region picker; managed instance list |
| 3 | Embedded terminal emulator, extra keys, resize |
| 4 | Foreground service, reconnection, biometric lock, clear errors |
| 5 | Signed APK on GitHub Releases, docs, demo |
| 6 | Plugin marketplace: RDS and DynamoDB plugins |

Progress is tracked in [milestones](https://github.com/jmanera/Mobile-aws-tools/milestones).
The app is distributed through GitHub Releases only, not Google Play.

## Contributing and security

See [CONTRIBUTING.md](CONTRIBUTING.md) and the [Code of Conduct](CODE_OF_CONDUCT.md).
Report vulnerabilities privately as described in [SECURITY.md](SECURITY.md).

## License

[Apache License 2.0](LICENSE). This project includes code from the AWS Session
Manager plugin (Apache-2.0) and `twinj/uuid` (MIT); see [NOTICE](NOTICE).

This project is not affiliated with or endorsed by Amazon Web Services.
