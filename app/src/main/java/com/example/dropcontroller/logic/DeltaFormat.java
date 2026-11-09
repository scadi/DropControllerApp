package com.example.dropcontroller.logic;

import androidx.annotation.NonNull;

import com.example.dropcontroller.data.DeltaDirection;
import com.example.dropcontroller.data.DeltaValue;

/**
 * Text form of a delta as shown on the Loop Page: the signed amount in brackets, for example "[+10]" or "[-5]".
 */
public final class DeltaFormat {

    private DeltaFormat() {
    }

    @NonNull
    public static String format(@NonNull DeltaValue delta) {
        if (delta.getAmount() == 0) {
            return "[0]";
        }
        return "[" + (delta.getDirection() == DeltaDirection.INC ? "+" : "-") + delta.getAmount() + "]";
    }
}
