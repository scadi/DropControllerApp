package com.example.dropcontroller.logic;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits the incoming byte stream into messages. A message is the text between '[' and ']'.
 * Bytes outside the brackets are ignored. Not thread safe; use from the reading thread only.
 */
public class MessageFramer {

    /** The longest message the device sends is far below this; longer ones are dropped. */
    private static final int MAX_PAYLOAD_LENGTH = 64;

    private final StringBuilder buffer = new StringBuilder();
    private boolean inMessage = false;

    /**
     * @return the complete messages (without brackets) found in the given bytes
     */
    @NonNull
    public List<String> feed(@NonNull byte[] data, int offset, int length) {
        List<String> messages = new ArrayList<>();
        for (int i = offset; i < offset + length; i++) {
            char c = (char) (data[i] & 0xFF);
            if (c == '[') {
                // A start marker always (re)starts a message
                buffer.setLength(0);
                inMessage = true;
            } else if (!inMessage) {
                continue;
            } else if (c == ']') {
                messages.add(buffer.toString());
                buffer.setLength(0);
                inMessage = false;
            } else if (buffer.length() >= MAX_PAYLOAD_LENGTH) {
                // Too long to be valid; drop it and wait for the next start marker
                buffer.setLength(0);
                inMessage = false;
            } else {
                buffer.append(c);
            }
        }
        return messages;
    }

    public void reset() {
        buffer.setLength(0);
        inMessage = false;
    }
}
