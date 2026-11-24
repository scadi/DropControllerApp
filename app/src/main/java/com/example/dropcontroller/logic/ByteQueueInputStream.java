package com.example.dropcontroller.logic;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * A blocking {@link InputStream} fed from another thread. The BLE notifications arrive in callbacks; they are put in
 * with {@link #offer(byte[])} and the reading thread takes them out like from a socket. {@link #close()} ends the
 * stream (read returns -1) and {@link #fail(IOException)} makes read throw, after the bytes already queued were read.
 */
public final class ByteQueueInputStream extends InputStream {

    private final Queue<byte[]> chunks = new ArrayDeque<>();
    // Offset in the chunk at the head of the queue
    private int headOffset = 0;
    private boolean closed = false;
    @Nullable
    private IOException failure;

    /** Adds bytes for the reader. Ignored after {@link #close()} or {@link #fail(IOException)}. */
    public synchronized void offer(@NonNull byte[] data) {
        if (closed || data.length == 0) {
            return;
        }
        chunks.add(data.clone());
        notifyAll();
    }

    /** Ends the stream: the reader gets the queued bytes, then -1. */
    @Override
    public synchronized void close() {
        closed = true;
        notifyAll();
    }

    /** Ends the stream with an error: the reader gets the queued bytes, then the exception. */
    public synchronized void fail(@NonNull IOException e) {
        if (!closed) {
            failure = e;
            closed = true;
            notifyAll();
        }
    }

    @Override
    public synchronized int read() throws IOException {
        byte[] one = new byte[1];
        int count = read(one, 0, 1);
        return count < 0 ? -1 : one[0] & 0xFF;
    }

    @Override
    public synchronized int read(@NonNull byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) {
            return 0;
        }
        while (chunks.isEmpty()) {
            if (closed) {
                if (failure != null) {
                    throw failure;
                }
                return -1;
            }
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for data", e);
            }
        }

        int copied = 0;
        while (copied < length && !chunks.isEmpty()) {
            byte[] head = chunks.peek();
            int count = Math.min(length - copied, head.length - headOffset);
            System.arraycopy(head, headOffset, buffer, offset + copied, count);
            copied += count;
            headOffset += count;
            if (headOffset == head.length) {
                chunks.remove();
                headOffset = 0;
            }
        }
        return copied;
    }

    @Override
    public synchronized int available() {
        int total = 0;
        boolean first = true;
        for (byte[] chunk : chunks) {
            total += first ? chunk.length - headOffset : chunk.length;
            first = false;
        }
        return total;
    }
}
