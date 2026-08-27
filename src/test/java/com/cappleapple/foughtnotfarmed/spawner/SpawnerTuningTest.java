package com.cappleapple.foughtnotfarmed.spawner;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cappleapple.foughtnotfarmed.config.CommonConfig.HealthMode;
import com.cappleapple.foughtnotfarmed.config.CommonConfig.MaxActiveMode;
import com.cappleapple.foughtnotfarmed.config.CommonConfig.PlayerRangeMode;
import net.minecraft.world.Difficulty;
import org.junit.jupiter.api.Test;

class SpawnerTuningTest {
    @Test
    void spawnCountUsesMultiplierAndHardCap() {
        assertEquals(6, SpawnerTuning.spawnCount(4, 1.5));
        assertEquals(64, SpawnerTuning.spawnCount(64, 16.0));
        assertEquals(0, SpawnerTuning.spawnCount(4, 0.0));
    }

    @Test
    void maxActiveInheritsOriginal() {
        assertEquals(6, SpawnerTuning.maxActive(6, MaxActiveMode.INHERIT, 10.0, 99));
    }

    @Test
    void maxActiveMultipliesOriginal() {
        assertEquals(9, SpawnerTuning.maxActive(6, MaxActiveMode.MULTIPLY, 1.5, 99));
    }

    @Test
    void maxActiveOverrideIsExact() {
        assertEquals(12, SpawnerTuning.maxActive(6, MaxActiveMode.OVERRIDE, 1.5, 12));
    }

    @Test
    void playerRangeSupportsAllModes() {
        assertEquals(16, SpawnerTuning.playerRange(16, PlayerRangeMode.INHERIT, 2.0, 30));
        assertEquals(32, SpawnerTuning.playerRange(16, PlayerRangeMode.MULTIPLY, 2.0, 30));
        assertEquals(30, SpawnerTuning.playerRange(16, PlayerRangeMode.FIXED, 2.0, 30));
    }

    @Test
    void spawnRangeIsBounded() {
        assertEquals(8, SpawnerTuning.spawnRange(4, 2.0));
        assertEquals(64, SpawnerTuning.spawnRange(64, 16.0));
    }

    @Test
    void fixedHealthIsPredictableOnEveryDifficulty() {
        assertEquals(36.0F, SpawnerTuning.health(36.0, HealthMode.FIXED, 1.0, 200.0, 4, Difficulty.HARD));
    }

    @Test
    void combinedHealthScalesAndClamps() {
        assertEquals(90.0F, SpawnerTuning.health(36.0, HealthMode.COMBINED, 1.0, 200.0, 8, Difficulty.HARD));
        assertEquals(50.0F, SpawnerTuning.health(36.0, HealthMode.COMBINED, 10.0, 50.0, 64, Difficulty.HARD));
    }
}
