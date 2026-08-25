package com.cappleapple.foughtnotfarmed.respawn;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;

/** Marker and state helpers for vanilla spawner blocks waiting to reactivate. */
public final class DormantSpawnerData {
    private static final String DORMANT_MARKER = "FoughtNotFarmedDormant";
    private static final String DORMANT_ID = "FoughtNotFarmedDormantId";
    private static final String DORMANT_POS = "FoughtNotFarmedDormantPos";
    private static final String DORMANT_DIMENSION = "FoughtNotFarmedDormantDimension";
    private static final String ORIGINAL_STATE = "FoughtNotFarmedDormantOriginalState";

    private DormantSpawnerData() {
    }

    public static void mark(SpawnerBlockEntity blockEntity, UUID dormantId, CompoundTag originalState) {
        CompoundTag data = blockEntity.getPersistentData();
        data.putBoolean(DORMANT_MARKER, true);
        data.putUUID(DORMANT_ID, dormantId);
        data.putLong(DORMANT_POS, blockEntity.getBlockPos().asLong());
        if (blockEntity.getLevel() != null) {
            data.putString(DORMANT_DIMENSION, blockEntity.getLevel().dimension().location().toString());
        }
        data.put(ORIGINAL_STATE, originalState.copy());
        blockEntity.setChanged();
    }

    public static boolean isDormant(SpawnerBlockEntity blockEntity) {
        CompoundTag data = blockEntity.getPersistentData();
        if (!data.getBoolean(DORMANT_MARKER)) {
            return false;
        }
        if (data.contains(DORMANT_POS, Tag.TAG_ANY_NUMERIC)
            && data.getLong(DORMANT_POS) != blockEntity.getBlockPos().asLong()) {
            return false;
        }
        return blockEntity.getLevel() == null
            || !data.contains(DORMANT_DIMENSION, Tag.TAG_STRING)
            || data.getString(DORMANT_DIMENSION).equals(blockEntity.getLevel().dimension().location().toString());
    }

    public static boolean matches(SpawnerBlockEntity blockEntity, UUID dormantId) {
        CompoundTag data = blockEntity.getPersistentData();
        return isDormant(blockEntity) && data.hasUUID(DORMANT_ID) && dormantId.equals(data.getUUID(DORMANT_ID));
    }

    public static Optional<UUID> id(SpawnerBlockEntity blockEntity) {
        CompoundTag data = blockEntity.getPersistentData();
        return data.hasUUID(DORMANT_ID) ? Optional.of(data.getUUID(DORMANT_ID)) : Optional.empty();
    }

    public static Optional<CompoundTag> originalState(SpawnerBlockEntity blockEntity) {
        CompoundTag data = blockEntity.getPersistentData();
        return data.contains(ORIGINAL_STATE, Tag.TAG_COMPOUND)
            ? Optional.of(data.getCompound(ORIGINAL_STATE).copy())
            : Optional.empty();
    }

    public static void clear(SpawnerBlockEntity blockEntity) {
        CompoundTag data = blockEntity.getPersistentData();
        data.remove(DORMANT_MARKER);
        data.remove(DORMANT_ID);
        data.remove(DORMANT_POS);
        data.remove(DORMANT_DIMENSION);
        data.remove(ORIGINAL_STATE);
        blockEntity.setChanged();
    }
}
