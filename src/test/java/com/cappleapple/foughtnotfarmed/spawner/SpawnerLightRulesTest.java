package com.cappleapple.foughtnotfarmed.spawner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.InclusiveRange;
import org.junit.jupiter.api.Test;

class SpawnerLightRulesTest {
    private static final InclusiveRange<Integer> DARK = new InclusiveRange<>(0, 0);

    @Test
    void customRulesEnforceBothLightChannelsWhenConfigured() {
        assertFalse(SpawnerLightRules.checkCustomLight(12, 15, DARK, DARK, false, false));
        assertTrue(SpawnerLightRules.checkCustomLight(0, 0, DARK, DARK, false, false));
    }

    @Test
    void blockAndSkyLightCanBeIgnoredIndependently() {
        assertTrue(SpawnerLightRules.checkCustomLight(12, 0, DARK, DARK, true, false));
        assertFalse(SpawnerLightRules.checkCustomLight(12, 15, DARK, DARK, true, false));
        assertTrue(SpawnerLightRules.checkCustomLight(0, 15, DARK, DARK, false, true));
        assertFalse(SpawnerLightRules.checkCustomLight(12, 15, DARK, DARK, false, true));
    }

    @Test
    void ignoringBothChannelsAlwaysPassesTheLightOnlyRules() {
        assertTrue(SpawnerLightRules.checkCustomLight(15, 15, DARK, DARK, true, true));
    }

    @Test
    void rawLightRemovesOnlyIgnoredChannels() {
        assertEquals(12, SpawnerLightRules.effectiveRawLight(6, 15, 3, false, false));
        assertEquals(12, SpawnerLightRules.effectiveRawLight(6, 15, 3, true, false));
        assertEquals(6, SpawnerLightRules.effectiveRawLight(6, 15, 3, false, true));
        assertEquals(0, SpawnerLightRules.effectiveRawLight(6, 15, 3, true, true));
    }
}
