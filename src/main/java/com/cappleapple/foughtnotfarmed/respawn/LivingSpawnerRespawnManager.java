package com.cappleapple.foughtnotfarmed.respawn;

import com.cappleapple.foughtnotfarmed.FoughtNotFarmed;
import com.cappleapple.foughtnotfarmed.config.CommonConfig;
import com.cappleapple.foughtnotfarmed.config.CommonConfig.RespawnClock;
import com.cappleapple.foughtnotfarmed.entity.LivingSpawnerEntity;
import com.cappleapple.foughtnotfarmed.registry.ModEntities;
import com.cappleapple.foughtnotfarmed.spawner.SpawnerState;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Persistent, per-dimension queue for Living Spawners destroyed by players or other damage. */
public final class LivingSpawnerRespawnManager extends SavedData {
    private static final String DATA_NAME = FoughtNotFarmed.MOD_ID + "_living_spawner_respawns";
    private static final String ENTRIES_KEY = "Entries";
    private static final long RETRY_TICKS = 400L;
    private static final long RETRY_MILLIS = 20_000L;
    private static final SavedData.Factory<LivingSpawnerRespawnManager> FACTORY = new SavedData.Factory<>(
        LivingSpawnerRespawnManager::new,
        LivingSpawnerRespawnManager::load
    );

    private final List<PendingRespawn> entries = new ArrayList<>();

    public static void schedule(ServerLevel level, LivingSpawnerEntity spawner) {
        if (!CommonConfig.RESPAWN_ENABLED.get() || spawner.spawnerState() == null) {
            return;
        }

        RespawnClock clock = CommonConfig.RESPAWN_CLOCK.get();
        double minutes = CommonConfig.RESPAWN_DELAY_MINUTES.get();
        boolean healthAdjusted = CommonConfig.HEALTH_ADJUSTED_RESPAWN_DELAY.get();
        double maximumHealth = spawner.getMaxHealth();
        long dueServerTick = clock == RespawnClock.SERVER_TIME
            ? saturatedAdd(level.getGameTime(), RespawnTiming.delayTicks(minutes, healthAdjusted, maximumHealth))
            : 0L;
        long dueEpochMillis = clock == RespawnClock.SYSTEM_TIME
            ? saturatedAdd(System.currentTimeMillis(), RespawnTiming.delayMillis(minutes, healthAdjusted, maximumHealth))
            : 0L;

        PendingRespawn entry = new PendingRespawn(
            spawner.blockPosition().immutable(),
            spawner.conversionSourcePos().immutable(),
            spawner.spawnerState().save(),
            clock,
            dueServerTick,
            dueEpochMillis
        );
        LivingSpawnerRespawnManager data = get(level);
        data.entries.add(entry);
        data.setDirty();
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
            || !CommonConfig.RESPAWN_ENABLED.get()
            || level.getGameTime() % 20L != 0L) {
            return;
        }
        get(level).tick(level, System.currentTimeMillis());
    }

    private static LivingSpawnerRespawnManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private void tick(ServerLevel level, long nowMillis) {
        boolean changed = false;
        Iterator<PendingRespawn> iterator = this.entries.iterator();
        while (iterator.hasNext()) {
            PendingRespawn entry = iterator.next();
            if (!entry.isDue(level.getGameTime(), nowMillis) || !isChunkLoaded(level, entry.deathPos())) {
                continue;
            }
            if (hasLivingSpawnerAt(level, entry.deathPos())) {
                iterator.remove();
                changed = true;
                continue;
            }

            StringBuilder decodeError = new StringBuilder();
            SpawnerState state = SpawnerState.decode(entry.spawnerState(), message -> {
                if (!decodeError.isEmpty()) {
                    decodeError.append("; ");
                }
                decodeError.append(message);
            }).orElse(null);
            if (state == null) {
                FoughtNotFarmed.LOGGER.error("Dropping invalid Living Spawner respawn at {}: {}", entry.deathPos(), decodeError);
                iterator.remove();
                changed = true;
                continue;
            }

            LivingSpawnerEntity replacement = ModEntities.LIVING_SPAWNER.get().create(level);
            if (replacement == null) {
                entry.postpone(level.getGameTime(), nowMillis);
                changed = true;
                continue;
            }
            replacement.initialize(entry.deathPos(), entry.conversionSourcePos(), state);
            if (!level.addFreshEntity(replacement)) {
                entry.postpone(level.getGameTime(), nowMillis);
                changed = true;
                continue;
            }

            iterator.remove();
            changed = true;
            level.playSound(null, entry.deathPos(), SoundEvents.TRIAL_SPAWNER_SPAWN_MOB, SoundSource.HOSTILE, 1.0F, 0.8F);
            level.sendParticles(
                ParticleTypes.SOUL_FIRE_FLAME,
                replacement.getX(), replacement.getY() + 0.5, replacement.getZ(),
                24, 0.4, 0.4, 0.4, 0.03
            );
        }
        if (changed) {
            this.setDirty();
        }
    }

    private static boolean hasLivingSpawnerAt(ServerLevel level, BlockPos pos) {
        AABB bounds = new AABB(pos).inflate(0.25);
        return !level.getEntitiesOfClass(
            LivingSpawnerEntity.class,
            bounds,
            entity -> entity.isAlive() && entity.sourcePos().equals(pos)
        ).isEmpty();
    }

    private static boolean isChunkLoaded(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(
            SectionPos.blockToSectionCoord(pos.getX()),
            SectionPos.blockToSectionCoord(pos.getZ())
        ) != null;
    }

    private static long saturatedAdd(long left, long right) {
        return right > 0L && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private LivingSpawnerRespawnManager() {
    }

    private static LivingSpawnerRespawnManager load(CompoundTag tag, HolderLookup.Provider registries) {
        LivingSpawnerRespawnManager data = new LivingSpawnerRespawnManager();
        ListTag list = tag.getList(ENTRIES_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            PendingRespawn.load(list.getCompound(index)).ifPresent(data.entries::add);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (PendingRespawn entry : this.entries) {
            list.add(entry.save());
        }
        tag.put(ENTRIES_KEY, list);
        return tag;
    }

    private static final class PendingRespawn {
        private final BlockPos deathPos;
        private final BlockPos conversionSourcePos;
        private final CompoundTag spawnerState;
        private final RespawnClock clock;
        private long dueServerTick;
        private long dueEpochMillis;

        private PendingRespawn(
            BlockPos deathPos,
            BlockPos conversionSourcePos,
            CompoundTag spawnerState,
            RespawnClock clock,
            long dueServerTick,
            long dueEpochMillis
        ) {
            this.deathPos = deathPos;
            this.conversionSourcePos = conversionSourcePos;
            this.spawnerState = spawnerState.copy();
            this.clock = clock;
            this.dueServerTick = dueServerTick;
            this.dueEpochMillis = dueEpochMillis;
        }

        private boolean isDue(long gameTime, long nowMillis) {
            return this.clock == RespawnClock.SERVER_TIME ? gameTime >= this.dueServerTick : nowMillis >= this.dueEpochMillis;
        }

        private void postpone(long gameTime, long nowMillis) {
            if (this.clock == RespawnClock.SERVER_TIME) {
                this.dueServerTick = saturatedAdd(gameTime, RETRY_TICKS);
            } else {
                this.dueEpochMillis = saturatedAdd(nowMillis, RETRY_MILLIS);
            }
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("DeathPos", this.deathPos.asLong());
            tag.putLong("ConversionSourcePos", this.conversionSourcePos.asLong());
            tag.put("SpawnerState", this.spawnerState.copy());
            tag.putString("Clock", this.clock.name());
            tag.putLong("DueServerTick", this.dueServerTick);
            tag.putLong("DueEpochMillis", this.dueEpochMillis);
            return tag;
        }

        private static java.util.Optional<PendingRespawn> load(CompoundTag tag) {
            if (!tag.contains("SpawnerState", Tag.TAG_COMPOUND)) {
                return java.util.Optional.empty();
            }
            RespawnClock clock;
            try {
                clock = RespawnClock.valueOf(tag.getString("Clock"));
            } catch (IllegalArgumentException exception) {
                clock = RespawnClock.SERVER_TIME;
            }
            return java.util.Optional.of(new PendingRespawn(
                BlockPos.of(tag.getLong("DeathPos")),
                BlockPos.of(tag.getLong("ConversionSourcePos")),
                tag.getCompound("SpawnerState"),
                clock,
                tag.getLong("DueServerTick"),
                tag.getLong("DueEpochMillis")
            ));
        }

        private BlockPos deathPos() {
            return this.deathPos;
        }

        private BlockPos conversionSourcePos() {
            return this.conversionSourcePos;
        }

        private CompoundTag spawnerState() {
            return this.spawnerState;
        }
    }
}
