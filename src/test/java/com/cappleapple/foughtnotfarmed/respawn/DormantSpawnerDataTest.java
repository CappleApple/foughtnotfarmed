package com.cappleapple.foughtnotfarmed.respawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import org.junit.jupiter.api.Test;

class DormantSpawnerDataTest {
    @Test
    void markerRoundTripsOriginalStateWithoutChangingIt() {
        CompoundTag original = new CompoundTag();
        original.putShort("RequiredPlayerRange", (short)16);
        original.putShort("SpawnCount", (short)4);
        CompoundTag spawnData = new CompoundTag();
        CompoundTag entity = new CompoundTag();
        entity.putString("id", "minecraft:zombie");
        spawnData.put("entity", entity);
        original.put("SpawnData", spawnData);
        UUID dormantId = UUID.randomUUID();
        SpawnerBlockEntity blockEntity = new SpawnerBlockEntity(BlockPos.ZERO, Blocks.SPAWNER.defaultBlockState());

        DormantSpawnerData.mark(blockEntity, dormantId, original);

        assertTrue(DormantSpawnerData.isDormant(blockEntity));
        assertTrue(DormantSpawnerData.matches(blockEntity, dormantId));
        assertEquals(dormantId, DormantSpawnerData.id(blockEntity).orElseThrow());
        assertEquals(original, DormantSpawnerData.originalState(blockEntity).orElseThrow());
        assertEquals(16, original.getShort("RequiredPlayerRange"));

        SpawnerBlockEntity movedCopy = new SpawnerBlockEntity(BlockPos.ZERO.above(), Blocks.SPAWNER.defaultBlockState());
        movedCopy.getPersistentData().merge(blockEntity.getPersistentData().copy());
        assertFalse(DormantSpawnerData.isDormant(movedCopy));

        DormantSpawnerData.clear(blockEntity);

        assertFalse(DormantSpawnerData.isDormant(blockEntity));
        assertTrue(DormantSpawnerData.id(blockEntity).isEmpty());
        assertTrue(DormantSpawnerData.originalState(blockEntity).isEmpty());
    }
}
