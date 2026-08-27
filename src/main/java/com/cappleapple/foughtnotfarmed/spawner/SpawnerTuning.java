package com.cappleapple.foughtnotfarmed.spawner;

import com.cappleapple.foughtnotfarmed.config.CommonConfig.HealthMode;
import com.cappleapple.foughtnotfarmed.config.CommonConfig.MaxActiveMode;
import com.cappleapple.foughtnotfarmed.config.CommonConfig.PlayerRangeMode;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;

public final class SpawnerTuning {
    private SpawnerTuning() {
    }

    public static int spawnCount(int original, double multiplier) {
        return Mth.clamp((int)Math.round(original * multiplier), 0, 64);
    }

    public static int maxActive(int original, MaxActiveMode mode, double multiplier, int overrideValue) {
        return switch (mode) {
            case INHERIT -> Mth.clamp(original, 0, 256);
            case MULTIPLY -> Mth.clamp((int)Math.round(original * multiplier), 0, 256);
            case OVERRIDE -> Mth.clamp(overrideValue, 0, 256);
        };
    }

    public static int playerRange(int original, PlayerRangeMode mode, double multiplier, int fixedValue) {
        return switch (mode) {
            case INHERIT -> Mth.clamp(original, 1, 256);
            case MULTIPLY -> Mth.clamp((int)Math.round(original * multiplier), 1, 256);
            case FIXED -> Mth.clamp(fixedValue, 1, 256);
        };
    }

    public static int spawnRange(int original, double multiplier) {
        return Mth.clamp((int)Math.round(original * multiplier), 0, 64);
    }

    public static float health(
        double baseHealth,
        HealthMode mode,
        double multiplier,
        double maximum,
        int originalSpawnCount,
        Difficulty difficulty
    ) {
        double spawnFactor = Math.max(1.0, originalSpawnCount / 4.0);
        double difficultyFactor = switch (difficulty) {
            case PEACEFUL -> 0.75;
            case EASY -> 0.9;
            case NORMAL -> 1.0;
            case HARD -> 1.25;
        };
        double scaled = switch (mode) {
            case FIXED -> baseHealth;
            case SPAWN_COUNT -> baseHealth * spawnFactor;
            case DIFFICULTY -> baseHealth * difficultyFactor;
            case COMBINED -> baseHealth * spawnFactor * difficultyFactor;
        };
        return (float)Mth.clamp(scaled * multiplier, 1.0, Math.max(1.0, maximum));
    }
}
