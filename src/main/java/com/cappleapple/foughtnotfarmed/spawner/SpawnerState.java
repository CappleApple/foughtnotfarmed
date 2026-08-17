package com.cappleapple.foughtnotfarmed.spawner;

import com.mojang.serialization.DataResult;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.world.level.SpawnData;

/**
 * A validated, mutable copy of vanilla {@code BaseSpawner} state.
 *
 * <p>The original tag is retained so data written by other mods is not discarded. Known vanilla
 * fields are normalized to bounded values before use and written back on entity save.</p>
 */
public final class SpawnerState {
    private static final int MAX_DELAY = 1_200_000;
    private static final int MAX_SPAWN_COUNT = 64;
    private static final int MAX_NEARBY = 256;
    private static final int MAX_RANGE = 256;

    private final CompoundTag sourceTag;
    private SimpleWeightedRandomList<SpawnData> spawnPotentials;
    private SpawnData nextSpawnData;
    private int spawnDelay;
    private final int minSpawnDelay;
    private final int maxSpawnDelay;
    private final int spawnCount;
    private final int maxNearbyEntities;
    private final int requiredPlayerRange;
    private final int spawnRange;

    private SpawnerState(
        CompoundTag sourceTag,
        SimpleWeightedRandomList<SpawnData> spawnPotentials,
        SpawnData nextSpawnData,
        int spawnDelay,
        int minSpawnDelay,
        int maxSpawnDelay,
        int spawnCount,
        int maxNearbyEntities,
        int requiredPlayerRange,
        int spawnRange
    ) {
        this.sourceTag = sourceTag;
        this.spawnPotentials = spawnPotentials;
        this.nextSpawnData = nextSpawnData;
        this.spawnDelay = spawnDelay;
        this.minSpawnDelay = minSpawnDelay;
        this.maxSpawnDelay = maxSpawnDelay;
        this.spawnCount = spawnCount;
        this.maxNearbyEntities = maxNearbyEntities;
        this.requiredPlayerRange = requiredPlayerRange;
        this.spawnRange = spawnRange;
    }

    public static Optional<SpawnerState> decode(CompoundTag tag, Consumer<String> errorSink) {
        CompoundTag source = tag.copy();
        SpawnData next = null;
        if (tag.contains("SpawnData", Tag.TAG_COMPOUND)) {
            DataResult<SpawnData> result = SpawnData.CODEC.parse(NbtOps.INSTANCE, tag.getCompound("SpawnData"));
            next = result.resultOrPartial(errorSink).orElse(null);
        }

        SimpleWeightedRandomList<SpawnData> potentials;
        if (tag.contains("SpawnPotentials", Tag.TAG_LIST)) {
            potentials = SpawnData.LIST_CODEC.parse(NbtOps.INSTANCE, tag.getList("SpawnPotentials", Tag.TAG_COMPOUND))
                .resultOrPartial(errorSink)
                .orElse(SimpleWeightedRandomList.empty());
        } else if (next != null) {
            potentials = SimpleWeightedRandomList.single(next);
        } else {
            potentials = SimpleWeightedRandomList.empty();
        }

        if (next == null && potentials.isEmpty()) {
            errorSink.accept("Spawner has neither valid SpawnData nor SpawnPotentials");
            return Optional.empty();
        }

        int minDelay = bounded(tag, "MinSpawnDelay", 200, 1, MAX_DELAY);
        int maxDelay = bounded(tag, "MaxSpawnDelay", 800, minDelay, MAX_DELAY);
        int delay = bounded(tag, "Delay", 20, -1, MAX_DELAY);
        int count = bounded(tag, "SpawnCount", 4, 1, MAX_SPAWN_COUNT);
        int nearby = bounded(tag, "MaxNearbyEntities", 6, 0, MAX_NEARBY);
        int playerRange = bounded(tag, "RequiredPlayerRange", 16, 1, MAX_RANGE);
        int range = bounded(tag, "SpawnRange", 4, 0, 64);

        SpawnerState state = new SpawnerState(
            source,
            potentials,
            next,
            delay,
            minDelay,
            maxDelay,
            count,
            nearby,
            playerRange,
            range
        );
        if (state.entityIds().isEmpty()) {
            errorSink.accept("Spawner has no valid entity IDs");
            return Optional.empty();
        }
        return Optional.of(state);
    }

    private static int bounded(CompoundTag tag, String key, int fallback, int min, int max) {
        int value = tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getInt(key) : fallback;
        return Mth.clamp(value, min, max);
    }

    public Set<ResourceLocation> entityIds() {
        Set<ResourceLocation> result = new LinkedHashSet<>();
        addEntityId(result, this.nextSpawnData);
        for (WeightedEntry.Wrapper<SpawnData> entry : this.spawnPotentials.unwrap()) {
            addEntityId(result, entry.data());
        }
        return Set.copyOf(result);
    }

    private static void addEntityId(Set<ResourceLocation> result, SpawnData data) {
        if (data == null) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(data.getEntityToSpawn().getString("id"));
        if (id != null) {
            result.add(id);
        }
    }

    public SpawnData current(RandomSource random) {
        if (this.nextSpawnData == null) {
            this.nextSpawnData = this.spawnPotentials.getRandom(random)
                .map(WeightedEntry.Wrapper::data)
                .orElseGet(SpawnData::new);
        }
        return this.nextSpawnData;
    }

    public void selectNext(RandomSource random) {
        this.spawnPotentials.getRandom(random).ifPresent(entry -> this.nextSpawnData = entry.data());
    }

    public void resetDelay(RandomSource random, double multiplier) {
        int raw = this.maxSpawnDelay <= this.minSpawnDelay
            ? this.minSpawnDelay
            : this.minSpawnDelay + random.nextInt(this.maxSpawnDelay - this.minSpawnDelay);
        this.spawnDelay = Mth.clamp((int)Math.round(raw * multiplier), 1, MAX_DELAY);
        this.selectNext(random);
    }

    public void shortRetryDelay() {
        this.spawnDelay = 20;
    }

    public void decrementDelay() {
        if (this.spawnDelay > 0) {
            this.spawnDelay--;
        }
    }

    public CompoundTag save() {
        CompoundTag tag = this.sourceTag.copy();
        tag.putInt("Delay", this.spawnDelay);
        tag.putInt("MinSpawnDelay", this.minSpawnDelay);
        tag.putInt("MaxSpawnDelay", this.maxSpawnDelay);
        tag.putInt("SpawnCount", this.spawnCount);
        tag.putInt("MaxNearbyEntities", this.maxNearbyEntities);
        tag.putInt("RequiredPlayerRange", this.requiredPlayerRange);
        tag.putInt("SpawnRange", this.spawnRange);
        if (this.nextSpawnData != null) {
            tag.put("SpawnData", SpawnData.CODEC.encodeStart(NbtOps.INSTANCE, this.nextSpawnData).getOrThrow());
        }
        tag.put("SpawnPotentials", SpawnData.LIST_CODEC.encodeStart(NbtOps.INSTANCE, this.spawnPotentials).getOrThrow());
        return tag;
    }

    public CompoundTag previewEntityTag(RandomSource random) {
        return this.current(random).getEntityToSpawn().copy();
    }

    public int spawnDelay() {
        return this.spawnDelay;
    }

    public int minSpawnDelay() {
        return this.minSpawnDelay;
    }

    public int maxSpawnDelay() {
        return this.maxSpawnDelay;
    }

    public int spawnCount() {
        return this.spawnCount;
    }

    public int maxNearbyEntities() {
        return this.maxNearbyEntities;
    }

    public int requiredPlayerRange() {
        return this.requiredPlayerRange;
    }

    public int spawnRange() {
        return this.spawnRange;
    }

    public int potentialCount() {
        return this.spawnPotentials.unwrap().size();
    }
}
