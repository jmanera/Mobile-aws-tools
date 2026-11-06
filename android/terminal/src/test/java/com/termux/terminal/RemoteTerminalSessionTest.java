// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package com.termux.terminal;

import android.os.Message;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

/**
 * The remote-stream TerminalSession (not upstream). On the JVM, Handler is a
 * stub that drops messages, so tests deliver them to the handler by hand.
 */
public class RemoteTerminalSessionTest extends TestCase {

    private final FakeTransport transport = new FakeTransport();
    private final FakeClient client = new FakeClient();
    private final TerminalSession session = new TerminalSession(transport, 1000, client);

    private void deliver(int what, Object obj) {
        Message msg = new Message();
        msg.what = what;
        msg.obj = obj;
        session.mMainThreadHandler.handleMessage(msg);
    }

    private String screen() {
        return session.getEmulator().getScreen().getTranscriptText();
    }

    public void testTheFirstSizeStartsTheEmulatorAndReachesTheRemoteSide() {
        session.updateSize(100, 30, 10, 20);
        assertEquals(100, session.getEmulator().mColumns);
        assertEquals(List.of("100x30"), transport.sizes);

        session.updateSize(60, 40, 10, 20);
        assertEquals(60, session.getEmulator().mColumns);
        assertEquals(List.of("100x30", "60x40"), transport.sizes);
    }

    public void testOutputIsShownOnceTheEmulatorExists() {
        session.onRemoteOutput("before size\r\n".getBytes(StandardCharsets.UTF_8), 0, 13);
        deliver(TerminalSession.MSG_NEW_INPUT, null); // dropped: no size yet, the output waits

        session.updateSize(80, 24, 10, 20);
        deliver(TerminalSession.MSG_NEW_INPUT, null);
        byte[] more = "\u001b[1mbold\u001b[0m after".getBytes(StandardCharsets.UTF_8);
        session.onRemoteOutput(more, 0, more.length);
        deliver(TerminalSession.MSG_NEW_INPUT, null);

        assertEquals("before size\nbold after", screen());
        assertTrue(client.textChanges > 0);
    }

    public void testOutputLargerThanOneChunkIsShownWhole() {
        session.updateSize(80, 24, 10, 20);
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 20_000; i++) big.append('x');
        byte[] bytes = big.toString().getBytes(StandardCharsets.UTF_8);
        session.onRemoteOutput(bytes, 0, bytes.length);
        deliver(TerminalSession.MSG_NEW_INPUT, null);

        assertEquals(20_000, screen().replace("\n", "").length());
    }

    public void testTypingGoesToTheTransportInUtf8() throws Exception {
        session.updateSize(80, 24, 10, 20);
        session.write("ls -l\r");
        session.writeCodePoint(true, 'b'); // Alt+b
        session.writeCodePoint(false, 'ñ');

        assertEquals("ls -l\r\u001bbñ", transport.awaitWritten(9));
    }

    public void testFinishingClosesTheRemoteSessionOnce() {
        session.updateSize(80, 24, 10, 20);
        session.finishIfRunning();
        assertEquals(1, transport.closes);

        deliver(TerminalSession.MSG_REMOTE_CLOSED, "Session closed by the user");
        assertFalse(session.isRunning());
        assertEquals(1, client.finished);
        assertTrue(screen(), screen().endsWith("[Session closed by the user]"));

        session.finishIfRunning();
        session.write("ignored");
        assertEquals(1, transport.closes);
    }

    public void testClosedBeforeTheViewHadASize() {
        byte[] bytes = "bye\r\n".getBytes(StandardCharsets.UTF_8);
        session.onRemoteOutput(bytes, 0, bytes.length);
        deliver(TerminalSession.MSG_REMOTE_CLOSED, "");
        assertFalse(session.isRunning());
        assertEquals(1, client.finished);

        session.updateSize(80, 24, 10, 20);
        assertEquals("bye\n\n[Session closed]", screen());
        assertTrue("a closed session does not resize the remote side", transport.sizes.isEmpty());
    }

    private static final class FakeTransport implements TerminalTransport {
        final List<String> sizes = new ArrayList<>();
        final ByteArrayOutputStream written = new ByteArrayOutputStream();
        int closes;

        @Override
        public void write(byte[] data, int offset, int count) {
            synchronized (written) {
                written.write(data, offset, count);
                written.notifyAll();
            }
        }

        @Override
        public void resize(int columns, int rows) {
            sizes.add(columns + "x" + rows);
        }

        @Override
        public void close() {
            closes++;
        }

        /** The writer thread forwards input asynchronously. */
        String awaitWritten(int chars) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            synchronized (written) {
                while (written.toString(StandardCharsets.UTF_8).length() < chars && System.nanoTime() < deadline) {
                    written.wait(100);
                }
                return written.toString(StandardCharsets.UTF_8);
            }
        }
    }

    private static final class FakeClient implements TerminalSessionClient {
        int textChanges;
        int finished;

        @Override public void onTextChanged(TerminalSession changedSession) { textChanges++; }
        @Override public void onTitleChanged(TerminalSession changedSession) { }
        @Override public void onSessionFinished(TerminalSession finishedSession) { finished++; }
        @Override public void onCopyTextToClipboard(TerminalSession session, String text) { }
        @Override public void onPasteTextFromClipboard(TerminalSession session) { }
        @Override public void onBell(TerminalSession session) { }
        @Override public void onColorsChanged(TerminalSession session) { }
        @Override public void onTerminalCursorStateChange(boolean state) { }
        @Override public Integer getTerminalCursorStyle() { return null; }
        @Override public void logError(String tag, String message) { }
        @Override public void logWarn(String tag, String message) { }
        @Override public void logInfo(String tag, String message) { }
        @Override public void logDebug(String tag, String message) { }
        @Override public void logVerbose(String tag, String message) { }
        @Override public void logStackTraceWithMessage(String tag, String message, Exception e) { }
        @Override public void logStackTrace(String tag, Exception e) { }
    }
}
