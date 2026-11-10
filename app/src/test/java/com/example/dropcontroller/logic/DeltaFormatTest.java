package com.example.dropcontroller.logic;

import static org.junit.Assert.assertEquals;

import com.example.dropcontroller.data.DeltaDirection;
import com.example.dropcontroller.data.DeltaValue;

import org.junit.Test;

public class DeltaFormatTest {

    @Test
    public void format_increment_hasPlusSign() {
        assertEquals("[+10]", DeltaFormat.format(new DeltaValue(DeltaDirection.INC, 10)));
    }

    @Test
    public void format_decrement_hasMinusSign() {
        assertEquals("[-5]", DeltaFormat.format(new DeltaValue(DeltaDirection.DEC, 5)));
    }

    @Test
    public void format_zeroAmount_hasNoSign() {
        assertEquals("[0]", DeltaFormat.format(new DeltaValue(DeltaDirection.DEC, 0)));
    }

    @Test
    public void format_amountIsClampedToMaxDelta() {
        assertEquals("[+999]", DeltaFormat.format(new DeltaValue(DeltaDirection.INC, 5000)));
    }
}
