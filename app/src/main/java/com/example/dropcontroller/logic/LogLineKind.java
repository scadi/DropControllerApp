package com.example.dropcontroller.logic;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies one line of the communication log written by {@link ProtocolFileLog}, so the log dialog can color the
 * message of the line (everything after the timestamp) and leave the timestamp in the normal text color.
 */
public enum LogLineKind {
    /** {@code > [..]}: a message written to the device. */
    SENT,
    /** {@code < [..]}: a message received from the device. */
    RECEIVED,
    /** {@code # ..}: a connection event that reports a failure (failed write or connection, lost connection). */
    ERROR,
    /** {@code # ..}: any other connection event; shown in the normal text color. */
    EVENT,
    /** A line that does not look like a log entry. */
    OTHER;

    // "yyyy-MM-dd HH:mm:ss.SSS " followed by the marker and the text of the entry
    private static final Pattern LINE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} (.+)$");

    /** The result of {@link #parse(String)}: the kind of the line and where its message starts. */
    public static final class Result {
        private final LogLineKind kind;
        private final int messageStart;

        private Result(@NonNull LogLineKind kind, int messageStart) {
            this.kind = kind;
            this.messageStart = messageStart;
        }

        @NonNull
        public LogLineKind getKind() {
            return kind;
        }

        /** Index in the line where the message starts (right after the timestamp and its space). */
        public int getMessageStart() {
            return messageStart;
        }
    }

    /**
     * @param line one log line without the line break
     * @return the kind of the line; for {@link #OTHER} the message start is 0
     */
    @NonNull
    public static Result parse(@NonNull String line) {
        Matcher matcher = LINE.matcher(line);
        if (!matcher.matches()) {
            return new Result(OTHER, 0);
        }
        String message = matcher.group(1);
        int start = matcher.start(1);
        if (message.startsWith("> ")) {
            return new Result(SENT, start);
        }
        if (message.startsWith("< ")) {
            return new Result(RECEIVED, start);
        }
        if (message.startsWith("# ")) {
            String text = message.toLowerCase(Locale.US);
            boolean error = text.contains("failed") || text.contains("lost");
            return new Result(error ? ERROR : EVENT, start);
        }
        return new Result(OTHER, 0);
    }
}
