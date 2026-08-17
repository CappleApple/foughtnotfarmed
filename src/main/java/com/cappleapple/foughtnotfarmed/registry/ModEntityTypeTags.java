package com.cappleapple.foughtnotfarmed.registry;

import com.cappleapple.foughtnotfarmed.FoughtNotFarmed;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

public final class ModEntityTypeTags {
    public static final TagKey<EntityType<?>> CANNOT_BE_SPAWNED = create("cannot_be_spawned_by_living_spawner");
    public static final TagKey<EntityType<?>> BOSSES = create("bosses");

    private ModEntityTypeTags() {
    }

    private static TagKey<EntityType<?>> create(String path) {
        return TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(FoughtNotFarmed.MOD_ID, path));
    }
}
