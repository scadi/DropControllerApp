package com.example.dropcontroller.logic;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Splits data into BLE packets. Without an MTU request a GATT write carries at most 20 bytes (the default
 * ATT MTU of 23 minus 3 bytes of header).
 */
public final class BleChunker {

    /** Largest payload of one write at the default MTU. */
    public static final int MAX_PACKET_SIZE = 20;

    private BleChunker() {
    }

    /** @return the data in packets of at most {@link #MAX_PACKET_SIZE} bytes, in order (empty for empty data) */
    @NonNull
    public static List<byte[]> split(@NonNull byte[] data) {
        return split(data, MAX_PACKET_SIZE);
    }

    /** @return the data in packets of at most {@code maxSize} bytes, in order (empty for empty data) */
    @NonNull
    public static List<byte[]> split(@NonNull byte[] data, int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive");
        }
        List<byte[]> packets = new ArrayList<>();
        for (int start = 0; start < data.length; start += maxSize) {
            packets.add(Arrays.copyOfRange(data, start, Math.min(start + maxSize, data.length)));
        }
        return packets;
    }
}
