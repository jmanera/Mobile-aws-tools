package com.termux.terminal;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * A terminal session, consisting of a remote shell coupled to a terminal interface.
 * <p>
 * DevOps Mobile Tools: adapted from upstream, where the session runs a local process through JNI.
 * Here output arrives through {@link #onRemoteOutput(byte[], int, int)} and {@link #onRemoteClosed(String)},
 * and input and size changes go to a {@link TerminalTransport}. The public API the view uses is unchanged.
 * See docs/adr/0002-vendor-termux-terminal.md.
 * <p>
 * When the size is made known by a call to {@link #updateSize(int, int, int, int)} terminal emulation will
 * begin. All terminal emulation and callback methods will be performed on the main thread.
 * <p>
 * The remote session may be ended by using the {@link #finishIfRunning()} method.
 * <p>
 * NOTE: The terminal session may outlive the EmulatorView, so be careful with callbacks!
 */
public final class TerminalSession extends TerminalOutput {

    static final int MSG_NEW_INPUT = 1;
    static final int MSG_REMOTE_CLOSED = 4;

    /** Remote output is queued in chunks of at most this size, each followed by a message to drain it. */
    private static final int CHUNK = 4096;

    public final String mHandle = UUID.randomUUID().toString();

    TerminalEmulator mEmulator;

    /**
     * A queue written to from the transport's thread when the remote side outputs, and read by main thread to
     * process by terminal emulator. Larger than upstream: network reads arrive in bursts.
     */
    final ByteQueue mProcessToTerminalIOQueue = new ByteQueue(64 * 1024);
    /**
     * A queue written to from the main thread due to user interaction, and read by another thread which forwards
     * to the {@link #mTransport}.
     */
    final ByteQueue mTerminalToProcessIOQueue = new ByteQueue(4096);
    /** Buffer to write translate code points into utf8 before writing to mTerminalToProcessIOQueue */
    private final byte[] mUtf8InputBuffer = new byte[5];

    /** Callback which gets notified when a session finishes or changes title. */
    TerminalSessionClient mClient;

    /** Whether the remote session is still open. */
    private boolean mRunning = true;

    /** Why the remote session ended, shown in the terminal; set on the main thread. */
    private String mCloseReason;

    /** Set by the application for user identification of session, not by terminal. */
    public String mSessionName;

    final Handler mMainThreadHandler = new MainThreadHandler();

    private final TerminalTransport mTransport;
    private final Integer mTranscriptRows;


    private static final String LOG_TAG = "TerminalSession";

    public TerminalSession(TerminalTransport transport, Integer transcriptRows, TerminalSessionClient client) {
        this.mTransport = transport;
        this.mTranscriptRows = transcriptRows;
        this.mClient = client;
    }

    /**
     * @param client The {@link TerminalSessionClient} interface implementation to allow
     *               for communication between {@link TerminalSession} and its client.
     */
    public void updateTerminalSessionClient(TerminalSessionClient client) {
        mClient = client;

        if (mEmulator != null)
            mEmulator.updateTerminalSessionClient(client);
    }

    /** Inform the remote side of the new size and reflow or initialize the emulator. */
    public void updateSize(int columns, int rows, int cellWidthPixels, int cellHeightPixels) {
        if (mEmulator == null) {
            initializeEmulator(columns, rows, cellWidthPixels, cellHeightPixels);
        } else {
            mEmulator.resize(columns, rows, cellWidthPixels, cellHeightPixels);
            if (isRunning()) mTransport.resize(columns, rows);
        }
    }

    /** The terminal title as set through escape sequences or null if none set. */
    public String getTitle() {
        return (mEmulator == null) ? null : mEmulator.getTitle();
    }

    /**
     * Set the terminal emulator's window size and start terminal emulation.
     *
     * @param columns The number of columns in the terminal window.
     * @param rows    The number of rows in the terminal window.
     */
    public void initializeEmulator(int columns, int rows, int cellWidthPixels, int cellHeightPixels) {
        mEmulator = new TerminalEmulator(this, columns, rows, cellWidthPixels, cellHeightPixels, mTranscriptRows, mClient);
        if (!isRunning()) {
            // Closed before the view knew its size: show what arrived and why it ended.
            drainOutput();
            showCloseReason();
            mProcessToTerminalIOQueue.close();
            return;
        }
        mTransport.resize(columns, rows);

        new Thread("TermSessionOutputWriter[" + mHandle + "]") {
            @Override
            public void run() {
                final byte[] buffer = new byte[4096];
                while (true) {
                    int bytesToWrite = mTerminalToProcessIOQueue.read(buffer, true);
                    if (bytesToWrite == -1) return;
                    try {
                        mTransport.write(buffer, 0, bytesToWrite);
                    } catch (RuntimeException e) {
                        Logger.logStackTraceWithMessage(mClient, LOG_TAG, "Error writing to the remote session", e);
                    }
                }
            }
        }.start();

        // Output that arrived before the view knew its size waits in the queue.
        mMainThreadHandler.sendEmptyMessage(MSG_NEW_INPUT);
    }

    /** Output from the remote side. May be called from any thread; blocks while the queue is full. */
    public void onRemoteOutput(byte[] data, int offset, int count) {
        while (count > 0) {
            int chunk = Math.min(count, CHUNK);
            if (!mProcessToTerminalIOQueue.write(data, offset, chunk)) return;
            mMainThreadHandler.sendEmptyMessage(MSG_NEW_INPUT);
            offset += chunk;
            count -= chunk;
        }
    }

    /** The remote session ended; [reason] is shown in the terminal. May be called from any thread. */
    public void onRemoteClosed(String reason) {
        mMainThreadHandler.sendMessage(mMainThreadHandler.obtainMessage(MSG_REMOTE_CLOSED, reason));
    }

    /** Write data to the remote shell. */
    @Override
    public void write(byte[] data, int offset, int count) {
        if (isRunning() && count > 0) mTerminalToProcessIOQueue.write(data, offset, count);
    }

    /** Write the Unicode code point to the terminal encoded in UTF-8. */
    public void writeCodePoint(boolean prependEscape, int codePoint) {
        if (codePoint > 1114111 || (codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
            // 1114111 (= 2**16 + 1024**2 - 1) is the highest code point, [0xD800,0xDFFF] is the surrogate range.
            throw new IllegalArgumentException("Invalid code point: " + codePoint);
        }

        int bufferPosition = 0;
        if (prependEscape) mUtf8InputBuffer[bufferPosition++] = 27;

        if (codePoint <= /* 7 bits */0b1111111) {
            mUtf8InputBuffer[bufferPosition++] = (byte) codePoint;
        } else if (codePoint <= /* 11 bits */0b11111111111) {
            /* 110xxxxx leading byte with leading 5 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b11000000 | (codePoint >> 6));
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        } else if (codePoint <= /* 16 bits */0b1111111111111111) {
            /* 1110xxxx leading byte with leading 4 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b11100000 | (codePoint >> 12));
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 6) & 0b111111));
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        } else { /* We have checked codePoint <= 1114111 above, so we have max 21 bits = 0b111111111111111111111 */
            /* 11110xxx leading byte with leading 3 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b11110000 | (codePoint >> 18));
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 12) & 0b111111));
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 6) & 0b111111));
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        }
        write(mUtf8InputBuffer, 0, bufferPosition);
    }

    public TerminalEmulator getEmulator() {
        return mEmulator;
    }

    /** Notify the {@link #mClient} that the screen has changed. */
    protected void notifyScreenUpdate() {
        mClient.onTextChanged(this);
    }

    /** Reset state for terminal emulator state. */
    public void reset() {
        mEmulator.reset();
        notifyScreenUpdate();
    }

    /** Finish this terminal session by closing the remote session. */
    public void finishIfRunning() {
        if (isRunning()) mTransport.close();
    }

    /** Feeds the emulator everything queued so far. Main thread, with an emulator. */
    private void drainOutput() {
        byte[] buffer = ((MainThreadHandler) mMainThreadHandler).mReceiveBuffer;
        int bytesRead;
        while ((bytesRead = mProcessToTerminalIOQueue.read(buffer, false)) > 0) {
            mEmulator.append(buffer, bytesRead);
        }
        notifyScreenUpdate();
    }

    private void showCloseReason() {
        byte[] bytesToWrite = ("\r\n[" + mCloseReason + "]").getBytes(StandardCharsets.UTF_8);
        mEmulator.append(bytesToWrite, bytesToWrite.length);
        notifyScreenUpdate();
    }

    @Override
    public void titleChanged(String oldTitle, String newTitle) {
        mClient.onTitleChanged(this);
    }

    public synchronized boolean isRunning() {
        return mRunning;
    }

    @Override
    public void onCopyTextToClipboard(String text) {
        mClient.onCopyTextToClipboard(this, text);
    }

    @Override
    public void onPasteTextFromClipboard() {
        mClient.onPasteTextFromClipboard(this);
    }

    @Override
    public void onBell() {
        mClient.onBell(this);
    }

    @Override
    public void onColorsChanged() {
        mClient.onColorsChanged(this);
    }

    @SuppressLint("HandlerLeak")
    class MainThreadHandler extends Handler {

        final byte[] mReceiveBuffer = new byte[4 * 1024];

        MainThreadHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            boolean closing = msg.what == MSG_REMOTE_CLOSED && isRunning();
            if (closing) {
                String reason = (String) msg.obj;
                mCloseReason = (reason == null || reason.isEmpty()) ? "Session closed" : reason;
                synchronized (TerminalSession.this) {
                    mRunning = false;
                }
                // Stops the writer thread; output already queued is still shown.
                mTerminalToProcessIOQueue.close();
            }

            // Until the view gives the emulator a size, output waits in the queue.
            if (mEmulator != null) {
                drainOutput();
                if (closing) {
                    showCloseReason();
                    mProcessToTerminalIOQueue.close();
                }
            }

            if (closing) mClient.onSessionFinished(TerminalSession.this);
        }

    }

}
