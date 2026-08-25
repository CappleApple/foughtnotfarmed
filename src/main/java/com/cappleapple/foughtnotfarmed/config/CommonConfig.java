package com.cappleapple.foughtnotfarmed.config;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class CommonConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public enum FilterMode {
        BLACKLIST,
        WHITELIST
    }

    public enum PlayerPlacedMode {
        CONVERT,
        KEEP,
        DISALLOW
    }

    public enum HealthMode {
        FIXED,
        SPAWN_COUNT,
        DIFFICULTY,
        COMBINED
    }

    public enum MaxActiveMode {
        INHERIT,
        MULTIPLY,
        OVERRIDE
    }

    public enum PlayerRangeMode {
        INHERIT,
        MULTIPLY,
        FIXED
    }

    public enum RespawnClock {
        SERVER_TIME,
        SYSTEM_TIME
    }

    public static final ModConfigSpec.BooleanValue AUTOMATIC_CONVERSION;
    public static final ModConfigSpec.DoubleValue CONVERSION_CHANCE;
    public static final ModConfigSpec.BooleanValue CONVERT_EXISTING_CHUNKS;
    public static final ModConfigSpec.BooleanValue CONVERT_RUNTIME_PLACED_SPAWNERS;
    public static final ModConfigSpec.EnumValue<PlayerPlacedMode> PLAYER_PLACED_MODE;
    public static final ModConfigSpec.EnumValue<FilterMode> ENTITY_FILTER_MODE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ENTITY_WHITELIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ENTITY_BLACKLIST;
    public static final ModConfigSpec.EnumValue<FilterMode> DIMENSION_FILTER_MODE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DIMENSION_WHITELIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DIMENSION_BLACKLIST;
    public static final ModConfigSpec.IntValue RUNTIME_SCAN_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue RUNTIME_CHUNKS_PER_SCAN;
    public static final ModConfigSpec.BooleanValue RELOCATE_ENCASED_SPAWNERS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXPOSURE_BLOCKS;
    public static final ModConfigSpec.IntValue MINIMUM_EXPOSED_SIDES;
    public static final ModConfigSpec.IntValue RELOCATION_SEARCH_RADIUS;

    public static final ModConfigSpec.DoubleValue BASE_HEALTH;
    public static final ModConfigSpec.EnumValue<HealthMode> HEALTH_MODE;
    public static final ModConfigSpec.DoubleValue HEALTH_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue MAX_SCALED_HEALTH;
    public static final ModConfigSpec.DoubleValue ARMOR;
    public static final ModConfigSpec.DoubleValue KNOCKBACK_RESISTANCE;
    public static final ModConfigSpec.BooleanValue EXPLOSION_DAMAGE;
    public static final ModConfigSpec.BooleanValue PROJECTILE_DAMAGE;
    public static final ModConfigSpec.BooleanValue MAGIC_DAMAGE;
    public static final ModConfigSpec.BooleanValue FIRE_DAMAGE;
    public static final ModConfigSpec.BooleanValue ENVIRONMENTAL_DAMAGE;
    public static final ModConfigSpec.DoubleValue DAMAGE_MULTIPLIER;

    public static final ModConfigSpec.BooleanValue RESPAWN_ENABLED;
    public static final ModConfigSpec.DoubleValue RESPAWN_DELAY_MINUTES;
    public static final ModConfigSpec.EnumValue<RespawnClock> RESPAWN_CLOCK;
    public static final ModConfigSpec.BooleanValue HEALTH_ADJUSTED_RESPAWN_DELAY;
    public static final ModConfigSpec.BooleanValue LEAVE_DORMANT_SPAWNER_BLOCK;

    public static final ModConfigSpec.DoubleValue SPAWN_COUNT_MULTIPLIER;
    public static final ModConfigSpec.EnumValue<MaxActiveMode> MAX_ACTIVE_MODE;
    public static final ModConfigSpec.DoubleValue MAX_ACTIVE_MULTIPLIER;
    public static final ModConfigSpec.IntValue MAX_ACTIVE_OVERRIDE;
    public static final ModConfigSpec.DoubleValue DELAY_MULTIPLIER;
    public static final ModConfigSpec.EnumValue<PlayerRangeMode> PLAYER_RANGE_MODE;
    public static final ModConfigSpec.DoubleValue PLAYER_RANGE_MULTIPLIER;
    public static final ModConfigSpec.IntValue FIXED_PLAYER_RANGE;
    public static final ModConfigSpec.DoubleValue SPAWN_RANGE_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue REQUIRE_LINE_OF_SIGHT;
    public static final ModConfigSpec.BooleanValue ALLOW_BOSS_ENTITIES;
    public static final ModConfigSpec.BooleanValue ACTIVATE_ON_PEACEFUL;
    public static final ModConfigSpec.IntValue SPAWN_WARNING_TICKS;

    public static final ModConfigSpec.BooleanValue XP_ENABLED;
    public static final ModConfigSpec.IntValue XP_AMOUNT;
    public static final ModConfigSpec.BooleanValue LOOT_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> LOOT_TABLE;
    public static final ModConfigSpec.BooleanValue DESPAWN_SUMMONS_ON_DEATH;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("conversion");
        AUTOMATIC_CONVERSION = BUILDER.comment("Automatically replace eligible vanilla spawner blocks with Living Spawners.")
            .define("enabled", true);
        CONVERSION_CHANCE = BUILDER.comment("One-time conversion probability. The result is stored on skipped spawner block entities.")
            .defineInRange("conversionChance", 1.0, 0.0, 1.0);
        CONVERT_EXISTING_CHUNKS = BUILDER.comment("Scan block entities when already-generated chunks load.")
            .define("convertExistingChunks", true);
        CONVERT_RUNTIME_PLACED_SPAWNERS = BUILDER.comment("Round-robin scan loaded chunks for spawners created by commands, structure blocks, scripts, or other mods.")
            .define("convertRuntimePlacedSpawners", true);
        PLAYER_PLACED_MODE = BUILDER.comment("CONVERT replaces player-placed spawners, KEEP marks them to remain blocks, DISALLOW cancels placement.")
            .defineEnum("playerPlacedMode", PlayerPlacedMode.CONVERT);
        ENTITY_FILTER_MODE = BUILDER.comment("BLACKLIST permits everything except entityBlacklist. WHITELIST permits only entityWhitelist.")
            .defineEnum("entityFilterMode", FilterMode.BLACKLIST);
        ENTITY_WHITELIST = idList("entityWhitelist");
        ENTITY_BLACKLIST = idList("entityBlacklist");
        DIMENSION_FILTER_MODE = BUILDER.comment("BLACKLIST permits dimensions except dimensionBlacklist. WHITELIST permits only dimensionWhitelist.")
            .defineEnum("dimensionFilterMode", FilterMode.BLACKLIST);
        DIMENSION_WHITELIST = idList("dimensionWhitelist");
        DIMENSION_BLACKLIST = idList("dimensionBlacklist");
        RUNTIME_SCAN_INTERVAL_TICKS = BUILDER.comment("Ticks between bounded round-robin runtime scans.")
            .defineInRange("runtimeScanIntervalTicks", 100, 20, 1200);
        RUNTIME_CHUNKS_PER_SCAN = BUILDER.comment("Maximum loaded chunks inspected per runtime scan. Only block-entity positions are examined.")
            .defineInRange("runtimeChunksPerScan", 8, 1, 128);
        RELOCATE_ENCASED_SPAWNERS = BUILDER.comment("Move an encased Living Spawner to the nearest loaded position that satisfies the exposure settings.")
            .define("relocateEncasedSpawners", true);
        EXPOSURE_BLOCKS = BUILDER.comment("Exact block IDs that count as open space and may contain a relocated Living Spawner.")
            .defineListAllowEmpty(
                "exposureBlocks",
                List.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:water"),
                () -> "minecraft:air",
                CommonConfig::isResourceLocation
            );
        MINIMUM_EXPOSED_SIDES = BUILDER.comment("Required exposed orthogonal faces (up, down, north, south, east, and west).")
            .defineInRange("minimumExposedSides", 2, 1, 6);
        RELOCATION_SEARCH_RADIUS = BUILDER.comment("Maximum Chebyshev distance searched without force-loading chunks when relocation is required.")
            .defineInRange("relocationSearchRadius", 8, 1, 16);
        BUILDER.pop();

        BUILDER.push("combat");
        BASE_HEALTH = BUILDER.defineInRange("baseHealth", 50.0, 1.0, 1024.0);
        HEALTH_MODE = BUILDER.comment("FIXED, SPAWN_COUNT, DIFFICULTY, or COMBINED scaling.")
            .defineEnum("healthMode", HealthMode.FIXED);
        HEALTH_MULTIPLIER = BUILDER.defineInRange("healthMultiplier", 1.0, 0.05, 100.0);
        MAX_SCALED_HEALTH = BUILDER.comment("Hard clamp after health scaling.")
            .defineInRange("maxScaledHealth", 200.0, 1.0, 1024.0);
        ARMOR = BUILDER.defineInRange("armor", 0.0, 0.0, 30.0);
        KNOCKBACK_RESISTANCE = BUILDER.defineInRange("knockbackResistance", 1.0, 0.0, 1.0);
        EXPLOSION_DAMAGE = BUILDER.define("explosionDamage", true);
        PROJECTILE_DAMAGE = BUILDER.define("projectileDamage", true);
        MAGIC_DAMAGE = BUILDER.define("magicDamage", true);
        FIRE_DAMAGE = BUILDER.define("fireDamage", true);
        ENVIRONMENTAL_DAMAGE = BUILDER.comment("Enables suffocation, drowning, falling, cactus, freezing, cramming, and similar non-combat damage.")
            .define("environmentalDamage", false);
        DAMAGE_MULTIPLIER = BUILDER.defineInRange("damageMultiplier", 1.0, 0.0, 100.0);
        BUILDER.pop();

        BUILDER.push("respawning");
        RESPAWN_ENABLED = BUILDER.comment("Schedule a replacement Living Spawner when one dies.")
            .define("enabled", true);
        RESPAWN_DELAY_MINUTES = BUILDER.comment("Minutes before a destroyed Living Spawner returns. Fractional minutes are supported.")
            .defineInRange("delayMinutes", 30.0, 0.0, 10080.0);
        RESPAWN_CLOCK = BUILDER.comment("SERVER_TIME counts loaded server ticks; SYSTEM_TIME includes time while the server is offline.")
            .defineEnum("clock", RespawnClock.SERVER_TIME);
        HEALTH_ADJUSTED_RESPAWN_DELAY = BUILDER.comment("Scale delay by maximum health divided by the default 50-health baseline.")
            .define("scaleDelayWithMaxHealth", false);
        LEAVE_DORMANT_SPAWNER_BLOCK = BUILDER.comment(
            "Replace a defeated Living Spawner with an inactive vanilla spawner block until it respawns."
        ).define("leaveDormantSpawnerBlock", false);
        BUILDER.pop();

        BUILDER.push("spawning");
        SPAWN_COUNT_MULTIPLIER = BUILDER.defineInRange("spawnCountMultiplier", 1.0, 0.0, 16.0);
        MAX_ACTIVE_MODE = BUILDER.defineEnum("maxActiveMode", MaxActiveMode.INHERIT);
        MAX_ACTIVE_MULTIPLIER = BUILDER.defineInRange("maxActiveMultiplier", 1.0, 0.0, 16.0);
        MAX_ACTIVE_OVERRIDE = BUILDER.defineInRange("maxActiveOverride", 6, 0, 256);
        DELAY_MULTIPLIER = BUILDER.defineInRange("delayMultiplier", 1.0, 0.05, 100.0);
        PLAYER_RANGE_MODE = BUILDER.defineEnum("playerRangeMode", PlayerRangeMode.INHERIT);
        PLAYER_RANGE_MULTIPLIER = BUILDER.defineInRange("playerRangeMultiplier", 1.0, 0.05, 16.0);
        FIXED_PLAYER_RANGE = BUILDER.defineInRange("fixedPlayerRange", 16, 1, 256);
        SPAWN_RANGE_MULTIPLIER = BUILDER.defineInRange("spawnRangeMultiplier", 1.0, 0.0, 16.0);
        REQUIRE_LINE_OF_SIGHT = BUILDER.define("requireLineOfSight", false);
        ALLOW_BOSS_ENTITIES = BUILDER.comment("Boss safety is datapack-extensible through the foughtnotfarmed:bosses entity type tag.")
            .define("allowBossEntities", false);
        ACTIVATE_ON_PEACEFUL = BUILDER.comment("If false, spawning timers pause on Peaceful. If true, normal placement rules still decide what may spawn.")
            .define("activateOnPeaceful", false);
        SPAWN_WARNING_TICKS = BUILDER.comment("Ticks before a spawn cycle when the cage begins shaking and plays its warning sound. Zero disables the warning.")
            .defineInRange("spawnWarningTicks", 40, 0, 1200);
        BUILDER.pop();

        BUILDER.push("rewards");
        XP_ENABLED = BUILDER.define("xpEnabled", true);
        XP_AMOUNT = BUILDER.defineInRange("xpAmount", 15, 0, 10000);
        LOOT_ENABLED = BUILDER.define("lootEnabled", false);
        LOOT_TABLE = BUILDER.comment("Loot table used when lootEnabled is true.")
            .define("lootTable", "minecraft:empty", CommonConfig::isResourceLocation);
        DESPAWN_SUMMONS_ON_DEATH = BUILDER.comment("By default, enemies already summoned remain alive after the Living Spawner is destroyed.")
            .define("despawnSummonsOnDeath", false);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private CommonConfig() {
    }

    private static ModConfigSpec.ConfigValue<List<? extends String>> idList(String name) {
        return BUILDER.defineListAllowEmpty(name, List.of(), () -> "minecraft:zombie", CommonConfig::isResourceLocation);
    }

    private static boolean isResourceLocation(Object value) {
        return value instanceof String string && ResourceLocation.tryParse(string) != null;
    }
}
