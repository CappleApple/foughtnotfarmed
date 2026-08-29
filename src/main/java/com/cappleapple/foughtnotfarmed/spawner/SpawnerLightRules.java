package com.cappleapple.foughtnotfarmed.spawner;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.InclusiveRange;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.SpawnData;
import net.neoforged.neoforge.event.EventHooks;

/** Applies Living Spawner light overrides without changing spawning elsewhere in the level. */
public final class SpawnerLightRules {
    private static final ThreadLocal<LightOverrides> ACTIVE_OVERRIDES = new ThreadLocal<>();

    private SpawnerLightRules() {
    }

    public static boolean checkCustomRules(
        SpawnData.CustomSpawnRules rules,
        ServerLevel level,
        BlockPos pos,
        boolean ignoreBlockLight,
        boolean ignoreSkyLight
    ) {
        return checkCustomLight(
            level.getBrightness(LightLayer.BLOCK, pos),
            level.getBrightness(LightLayer.SKY, pos),
            rules.blockLightLimit(),
            rules.skyLightLimit(),
            ignoreBlockLight,
            ignoreSkyLight
        );
    }

    static boolean checkCustomLight(
        int blockLight,
        int skyLight,
        InclusiveRange<Integer> blockLimit,
        InclusiveRange<Integer> skyLimit,
        boolean ignoreBlockLight,
        boolean ignoreSkyLight
    ) {
        return (ignoreBlockLight || blockLimit.isValueInRange(blockLight))
            && (ignoreSkyLight || skyLimit.isValueInRange(skyLight));
    }

    public static <T extends Entity> boolean checkSpawnRules(
        EntityType<T> type,
        ServerLevelAccessor level,
        BlockPos pos,
        RandomSource random,
        boolean ignoreBlockLight,
        boolean ignoreSkyLight
    ) {
        if (!ignoreBlockLight && !ignoreSkyLight) {
            return SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.SPAWNER, pos, random);
        }

        return withOverrides(
            ignoreBlockLight,
            ignoreSkyLight,
            () -> SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.SPAWNER, pos, random)
        );
    }

    public static boolean checkSpawnerPosition(
        Mob mob,
        ServerLevelAccessor level,
        SpawnData spawnData,
        BaseSpawner spawner,
        boolean ignoreBlockLight,
        boolean ignoreSkyLight
    ) {
        if (!ignoreBlockLight && !ignoreSkyLight) {
            return EventHooks.checkSpawnPositionSpawner(mob, level, MobSpawnType.SPAWNER, spawnData, spawner);
        }
        return withOverrides(
            ignoreBlockLight,
            ignoreSkyLight,
            () -> EventHooks.checkSpawnPositionSpawner(mob, level, MobSpawnType.SPAWNER, spawnData, spawner)
        );
    }

    /** Returns a result only while this thread is evaluating a Living Spawner placement. */
    @Nullable
    public static Boolean monsterLightOverride(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
        LightOverrides overrides = ACTIVE_OVERRIDES.get();
        if (overrides == null) {
            return null;
        }

        int skyLight = level.getBrightness(LightLayer.SKY, pos);
        if (!overrides.ignoreSkyLight() && skyLight > random.nextInt(32)) {
            return false;
        }

        int blockLight = level.getBrightness(LightLayer.BLOCK, pos);
        int blockLimit = level.dimensionType().monsterSpawnBlockLightLimit();
        if (!overrides.ignoreBlockLight() && blockLimit < 15 && blockLight > blockLimit) {
            return false;
        }

        int effectiveRawLight = effectiveRawLight(level, pos, overrides);
        return effectiveRawLight <= level.dimensionType().monsterSpawnLightTest().sample(random);
    }

    /** Returns a result only while NeoForge evaluates a Living Spawner's prepared monster. */
    @Nullable
    public static Float monsterWalkTargetOverride(LevelReader level, BlockPos pos) {
        LightOverrides overrides = ACTIVE_OVERRIDES.get();
        if (overrides == null || !(level instanceof ServerLevelAccessor serverLevel)) {
            return null;
        }

        int effectiveRawLight = effectiveRawLight(serverLevel, pos, overrides);
        float normalizedLight = (float)effectiveRawLight / 15.0F;
        float curvedLight = normalizedLight / (4.0F - 3.0F * normalizedLight);
        float lightValue = level.dimensionType().ambientLight()
            + curvedLight * (1.0F - level.dimensionType().ambientLight());
        return 0.5F - lightValue;
    }

    /** Returns a result only while a Living Spawner evaluates a vanilla bright-light rule. */
    @Nullable
    public static Boolean brightLightOverride(BlockAndTintGetter level, BlockPos pos, int minimumExclusive) {
        LightOverrides overrides = ACTIVE_OVERRIDES.get();
        if (overrides == null || !(level instanceof ServerLevelAccessor serverLevel)) {
            return null;
        }
        if (overrides.ignoreBlockLight() && overrides.ignoreSkyLight()) {
            return true;
        }
        return effectiveRawLight(serverLevel, pos, overrides) > minimumExclusive;
    }

    /** Returns selective raw light only while a Living Spawner evaluates a vanilla light rule. */
    @Nullable
    public static Integer rawLightOverride(BlockAndTintGetter level, BlockPos pos) {
        LightOverrides overrides = ACTIVE_OVERRIDES.get();
        if (overrides == null || !(level instanceof ServerLevelAccessor serverLevel)) {
            return null;
        }
        return effectiveRawLight(serverLevel, pos, overrides);
    }

    /** Returns a result only while a Living Spawner evaluates a block-light-only rule. */
    @Nullable
    public static Boolean blockLightAtMostOverride(BlockAndTintGetter level, BlockPos pos, int maximumInclusive) {
        LightOverrides overrides = ACTIVE_OVERRIDES.get();
        if (overrides == null) {
            return null;
        }
        return overrides.ignoreBlockLight() || level.getBrightness(LightLayer.BLOCK, pos) <= maximumInclusive;
    }

    private static int effectiveRawLight(ServerLevelAccessor level, BlockPos pos, LightOverrides overrides) {
        int skyDarken = level.getLevel().isThundering() ? 10 : level.getSkyDarken();
        return effectiveRawLight(
            level.getBrightness(LightLayer.BLOCK, pos),
            level.getBrightness(LightLayer.SKY, pos),
            skyDarken,
            overrides.ignoreBlockLight(),
            overrides.ignoreSkyLight()
        );
    }

    static int effectiveRawLight(int blockLight, int skyLight, int skyDarken, boolean ignoreBlockLight, boolean ignoreSkyLight) {
        int effectiveBlockLight = ignoreBlockLight ? 0 : blockLight;
        int effectiveSkyLight = ignoreSkyLight ? 0 : Math.max(0, skyLight - skyDarken);
        return Math.max(effectiveBlockLight, effectiveSkyLight);
    }

    private static boolean withOverrides(boolean ignoreBlockLight, boolean ignoreSkyLight, BooleanSupplier action) {
        LightOverrides previous = ACTIVE_OVERRIDES.get();
        ACTIVE_OVERRIDES.set(new LightOverrides(ignoreBlockLight, ignoreSkyLight));
        try {
            return action.getAsBoolean();
        } finally {
            if (previous == null) {
                ACTIVE_OVERRIDES.remove();
            } else {
                ACTIVE_OVERRIDES.set(previous);
            }
        }
    }

    private record LightOverrides(boolean ignoreBlockLight, boolean ignoreSkyLight) {
    }

    @FunctionalInterface
    private interface BooleanSupplier {
        boolean getAsBoolean();
    }
}
