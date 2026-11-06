# 0002. Vendor the Termux terminal emulator and view

- Status: Accepted
- Date: 2026-11-06
- Phase: 3 (terminal)

## Context

Phase 3 replaces the plain-text output of phases 0–2 with a real terminal:
the acceptance criterion is that vim, htop and less render and respond
correctly. That needs a VT100/xterm emulator (escape sequences, alternate
screen, scroll regions, colours, mouse and bracketed paste modes, wide
characters) and an Android view that draws it and handles the keyboard,
including the input method (IME) of on-screen keyboards.

Options considered:

| Option | For | Against |
|--------|-----|---------|
| Termux `terminal-emulator` + `terminal-view`, vendored | The most used terminal on Android; native view with mature IME handling, selection, scrollback and pinch zoom; Apache-2.0 | Its `TerminalSession` runs a local process through JNI; we carry a copy |
| The same libraries from JitPack | No copy in the repository | Adds an external Maven repository to a build that only trusts Google and Maven Central; the JNI session still has to be worked around |
| xterm.js in a WebView | Excellent rendering, resize through its fit addon | Known problems with Android keyboards inside a WebView (composition, Gboard); every byte crosses a JavaScript bridge |
| Writing an emulator | Full control | Months of work to reach what the others already pass |

## Findings

Read at tag `v0.118.3` of [termux/termux-app](https://github.com/termux/termux-app)
(`5b657c6adf4304e5198951ce815fe0205dcac29c`). The repository is GPLv3, but
its `LICENSE.md` excepts `terminal-emulator` and `terminal-view`, derived
from Jack Palevich's Terminal Emulator for Android, which stay Apache-2.0.

- `TerminalEmulator` only talks to a `TerminalOutput`: it has no idea a
  process exists. It ships with 145 unit tests.
- `TerminalView` uses a handful of `TerminalSession` methods: `getEmulator`,
  `write`, `writeCodePoint`, `updateSize` and the clipboard callbacks.
- `TerminalSession` is the only class tied to a local process: it calls
  `JNI.createSubprocess`, reads and writes the pty file descriptor on two
  threads, waits for the process and sends it SIGKILL.

## Decision

Copy both libraries into the `terminal` module, keeping their
`com.termux.*` packages so a diff against upstream stays readable. The first
commit is the unmodified copy; changes come in later commits.

Changes to upstream:

- **`TerminalSession` talks to a `TerminalTransport`** (new interface)
  instead of a process. Remote output arrives through `onRemoteOutput`,
  queued in 4 KiB chunks and drained on the main thread as upstream does;
  the end of the session through `onRemoteClosed(reason)`, which shows the
  reason in the terminal. Input goes through the same writer thread to
  `TerminalTransport.write`; size changes go to `TerminalTransport.resize`;
  `finishIfRunning` calls `TerminalTransport.close`. Output that arrives
  before the view knows its size waits in the queue, and a session that ends
  before then is still reported. The output queue grows from 4 KiB to
  64 KiB, since network reads come in bursts. `getPid`, `getCwd` and
  `getExitStatus` are gone; nothing used them.
- **No native code**: `JNI.java` and `src/main/jni` are not copied.
- **Resources**: the two text-selection classes import the module's `R`
  (`io.github.jmanera.devopsmobiletools.terminal.R`) instead of
  `com.termux.view.R`.

The SSM bridge already takes the terminal size (`Session.Resize`), so the
transport maps directly onto `TerminalConnection.write`, `resize` and
`close` from plugin-api.

## Consequences

- Fixes from Termux arrive by copying a newer tag over the baseline commit
  and reapplying the changes above; `RemoteTerminalSessionTest` covers the
  adapted session, the upstream tests cover the emulator.
- The app gains about 8,000 lines of Java it did not write. They are
  covered by upstream's tests and used by millions of Termux installs.
- NOTICE credits Termux and Terminal Emulator for Android.
