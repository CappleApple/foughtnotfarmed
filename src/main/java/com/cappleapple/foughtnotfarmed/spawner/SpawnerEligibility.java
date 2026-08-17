package com.cappleapple.foughtnotfarmed.spawner;

import com.cappleapple.foughtnotfarmed.config.CommonConfig;
import com.cappleapple.foughtnotfarmed.registry.ModEntityTypeTags;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;

public final class SpawnerEligibility {
    public record Result(boolean eligible, String reason) {
        public static Result allow() {
            return new Result(true, "eligible");
        }

        public static Result deny(String reason) {
            return new Result(false, reason);
        }
    }

    private SpawnerEligibility() {
    }

    public static Result evaluate(ServerLevel level, SpawnerState state) {
        String dimension = level.dimension().location().toString();
        if (!matchesFilter(
            dimension,
            CommonConfig.DIMENSION_FILTER_MODE.get(),
            CommonConfig.DIMENSION_WHITELIST.get(),
            CommonConfig.DIMENSION_BLACKLIST.get()
        )) {
            return Result.deny("dimension filter rejected " + dimension);
        }

        for (ResourceLocation id : state.entityIds()) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
            if (type == null) {
                return Result.deny("unregistered entity type " + id);
            }
            if (type.is(ModEntityTypeTags.CANNOT_BE_SPAWNED)) {
                return Result.deny(id + " is tagged as unsupported");
            }
            if (!CommonConfig.ALLOW_BOSS_ENTITIES.get() && type.is(ModEntityTypeTags.BOSSES)) {
                return Result.deny(id + " is tagged as a boss");
            }
            if (!matchesFilter(
                id.toString(),
                CommonConfig.ENTITY_FILTER_MODE.get(),
                CommonConfig.ENTITY_WHITELIST.get(),
                CommonConfig.ENTITY_BLACKLIST.get()
            )) {
                return Result.deny("entity filter rejected " + id);
            }
        }
        return Result.allow();
    }

    static boolean matchesFilter(
        String value,
        CommonConfig.FilterMode mode,
        List<? extends String> whitelist,
        List<? extends String> blacklist
    ) {
        return mode == CommonConfig.FilterMode.WHITELIST ? whitelist.contains(value) : !blacklist.contains(value);
    }
}
