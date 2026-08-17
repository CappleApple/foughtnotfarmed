package com.cappleapple.foughtnotfarmed.registry;

import com.cappleapple.foughtnotfarmed.FoughtNotFarmed;
import com.cappleapple.foughtnotfarmed.entity.LivingSpawnerEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, FoughtNotFarmed.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<LivingSpawnerEntity>> LIVING_SPAWNER = ENTITY_TYPES.register(
        "living_spawner",
        () -> EntityType.Builder.of(LivingSpawnerEntity::new, MobCategory.MONSTER)
            .sized(1.0F, 1.0F)
            .noSummon()
            .clientTrackingRange(10)
            .updateInterval(3)
            .build(FoughtNotFarmed.MOD_ID + ":living_spawner")
    );

    private ModEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(LIVING_SPAWNER.get(), LivingSpawnerEntity.createAttributes().build());
    }
}
