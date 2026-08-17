package com.cappleapple.foughtnotfarmed.spawner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.level.SpawnData;
import org.junit.jupiter.api.Test;

class SpawnerStateTest {
    @Test
    void decodesVanillaDefaultsWhenOptionalNumbersAreMissing() {
        CompoundTag tag = tagWithCurrent("minecraft:zombie");

        SpawnerState state = SpawnerState.decode(tag, message -> {}).orElseThrow();

        assertEquals(20, state.spawnDelay());
        assertEquals(200, state.minSpawnDelay());
        assertEquals(800, state.maxSpawnDelay());
        assertEquals(4, state.spawnCount());
        assertEquals(6, state.maxNearbyEntities());
        assertEquals(16, state.requiredPlayerRange());
        assertEquals(4, state.spawnRange());
    }

    @Test
    void preservesWeightedSpawnPotentialsAndAllEntityIds() {
        SpawnData zombie = spawnData("minecraft:zombie");
        SpawnData skeleton = spawnData("minecraft:skeleton");
        SimpleWeightedRandomList<SpawnData> potentials = SimpleWeightedRandomList.<SpawnData>builder()
            .add(zombie, 3)
            .add(skeleton, 1)
            .build();
        CompoundTag tag = tagWithCurrent("minecraft:zombie");
        tag.put("SpawnPotentials", SpawnData.LIST_CODEC.encodeStart(NbtOps.INSTANCE, potentials).getOrThrow());

        SpawnerState state = SpawnerState.decode(tag, message -> {}).orElseThrow();
        SpawnerState roundTripped = SpawnerState.decode(state.save(), message -> {}).orElseThrow();

        assertEquals(2, roundTripped.potentialCount());
        assertEquals(
            java.util.Set.of(ResourceLocation.parse("minecraft:zombie"), ResourceLocation.parse("minecraft:skeleton")),
            roundTripped.entityIds()
        );
    }

    @Test
    void retainsCustomEntityNbtForModdedSpawners() {
        CompoundTag entity = new CompoundTag();
        entity.putString("id", "example:goblin");
        entity.putString("Variant", "champion");
        SpawnData data = new SpawnData(entity, Optional.empty(), Optional.empty());
        CompoundTag tag = new CompoundTag();
        tag.put("SpawnData", SpawnData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow());

        SpawnerState state = SpawnerState.decode(tag, message -> {}).orElseThrow();

        assertEquals("champion", state.previewEntityTag(RandomSource.create(1L)).getString("Variant"));
        assertEquals("champion", SpawnerState.decode(state.save(), message -> {}).orElseThrow()
            .previewEntityTag(RandomSource.create(1L)).getString("Variant"));
    }

    @Test
    void rejectsEmptySpawnerWithoutCrashing() {
        List<String> errors = new ArrayList<>();

        assertTrue(SpawnerState.decode(new CompoundTag(), errors::add).isEmpty());
        assertFalse(errors.isEmpty());
    }

    @Test
    void clampsMalformedNumericSettingsToSafetyBounds() {
        CompoundTag tag = tagWithCurrent("minecraft:zombie");
        tag.putInt("SpawnCount", Integer.MAX_VALUE);
        tag.putInt("MaxNearbyEntities", Integer.MAX_VALUE);
        tag.putInt("RequiredPlayerRange", -50);
        tag.putInt("SpawnRange", Integer.MAX_VALUE);
        tag.putInt("MinSpawnDelay", -10);
        tag.putInt("MaxSpawnDelay", -10);

        SpawnerState state = SpawnerState.decode(tag, message -> {}).orElseThrow();

        assertEquals(64, state.spawnCount());
        assertEquals(256, state.maxNearbyEntities());
        assertEquals(1, state.requiredPlayerRange());
        assertEquals(64, state.spawnRange());
        assertEquals(1, state.minSpawnDelay());
        assertEquals(1, state.maxSpawnDelay());
    }

    @Test
    void resetDelayHonorsOriginalRangeAndMultiplier() {
        CompoundTag tag = tagWithCurrent("minecraft:zombie");
        tag.putInt("MinSpawnDelay", 200);
        tag.putInt("MaxSpawnDelay", 800);
        SpawnerState state = SpawnerState.decode(tag, message -> {}).orElseThrow();

        state.resetDelay(RandomSource.create(42L), 0.5);

        assertTrue(state.spawnDelay() >= 100);
        assertTrue(state.spawnDelay() < 400);
    }

    private static CompoundTag tagWithCurrent(String id) {
        CompoundTag tag = new CompoundTag();
        tag.put("SpawnData", SpawnData.CODEC.encodeStart(NbtOps.INSTANCE, spawnData(id)).getOrThrow());
        return tag;
    }

    private static SpawnData spawnData(String id) {
        CompoundTag entity = new CompoundTag();
        entity.putString("id", id);
        return new SpawnData(entity, Optional.empty(), Optional.empty());
    }
}
