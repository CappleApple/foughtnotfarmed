package com.cappleapple.foughtnotfarmed.respawn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RespawnTimingTest {
    @Test
    void defaultDelayIsThirtyMinutesInBothClockUnits() {
        assertEquals(36_000L, RespawnTiming.delayTicks(30.0, false, 50.0));
        assertEquals(1_800_000L, RespawnTiming.delayMillis(30.0, false, 50.0));
    }

    @Test
    void disabledHealthAdjustmentIgnoresMaximumHealth() {
        assertEquals(36_000L, RespawnTiming.delayTicks(30.0, false, 200.0));
    }

    @Test
    void healthAdjustmentUsesFiftyHealthAsNeutralBaseline() {
        assertEquals(36_000L, RespawnTiming.delayTicks(30.0, true, 50.0));
        assertEquals(72_000L, RespawnTiming.delayTicks(30.0, true, 100.0));
        assertEquals(18_000L, RespawnTiming.delayTicks(30.0, true, 25.0));
    }

    @Test
    void zeroDelayRemainsZeroWhenHealthAdjusted() {
        assertEquals(0L, RespawnTiming.delayTicks(0.0, true, 1024.0));
    }
}
