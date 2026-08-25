package com.cappleapple.foughtnotfarmed.conversion;

import com.cappleapple.foughtnotfarmed.FoughtNotFarmed;
import com.cappleapple.foughtnotfarmed.config.CommonConfig;
import com.cappleapple.foughtnotfarmed.entity.LivingSpawnerEntity;
import com.cappleapple.foughtnotfarmed.registry.ModEntities;
import com.cappleapple.foughtnotfarmed.respawn.DormantSpawnerData;
import com.cappleapple.foughtnotfarmed.spawner.SpawnerEligibility;
import com.cappleapple.foughtnotfarmed.spawner.SpawnerState;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Performs all world interaction on the server thread. Chunk events only enqueue work; this is
 * important because NeoForge documents that chunk-load events may fire before a chunk is FULL.
 * Scans examine block-entity positions, never every block in a chunk.
 */
public final class SpawnerConversionManager {
    public enum Cause {
        AUTOMATIC,
        PLAYER_PLACED,
        COMMAND
    }

    public record ConversionResult(boolean converted, String message) {
        static ConversionResult success(String message) {
            return new ConversionResult(true, message);
        }

        static ConversionResult skipped(String message) {
            return new ConversionResult(false, message);
        }
    }

    public record ScanResult(int found, int converted) {
        public ScanResult add(ScanResult other) {
            return new ScanResult(this.found + other.found, this.converted + other.converted);
        }
    }

    private static final String PLAYER_KEEP_MARKER = "FoughtNotFarmedPlayerPlacedKeep";
    private static final String CHANCE_ROLLED_MARKER = "FoughtNotFarmedChanceRolled";
    private static final String CHANCE_RESULT_MARKER = "FoughtNotFarmedChanceResult";
    private static final int INITIAL_SCANS_PER_TICK = 8;
    private static final int POSITIONS_PER_TICK = 64;
    private static final Map<ServerLevel, LevelScanState> LEVEL_STATES = new WeakHashMap<>();

    private SpawnerConversionManager() {
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        long chunkKey = event.getChunk().getPos().toLong();
        boolean newChunk = event.isNewChunk();
        level.getServer().execute(() -> {
            LevelScanState state = state(level);
            state.trackChunk(chunkKey);
            if (CommonConfig.AUTOMATIC_CONVERSION.get() && (newChunk || CommonConfig.CONVERT_EXISTING_CHUNKS.get())) {
                state.queueInitialScan(chunkKey);
            }
        });
    }

    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        long chunkKey = event.getChunk().getPos().toLong();
        level.getServer().execute(() -> {
            LevelScanState state = LEVEL_STATES.get(level);
            if (state != null) {
                state.untrackChunk(chunkKey);
            }
        });
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            level.getServer().execute(() -> LEVEL_STATES.remove(level));
        }
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        LevelScanState state = LEVEL_STATES.get(level);
        if (state == null) {
            return;
        }
        state.processQueuedPositions(level);
        state.processInitialScans(level);
        state.processRuntimeScans(level);
    }

    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !event.getPlacedBlock().is(Blocks.SPAWNER)) {
            return;
        }
        BlockPos pos = event.getPos().immutable();
        if (event.getEntity() instanceof Player) {
            switch (CommonConfig.PLAYER_PLACED_MODE.get()) {
                case DISALLOW -> event.setCanceled(true);
                case KEEP -> {
                    if (!markPlayerPlacedKeep(level, pos)) {
                        level.getServer().execute(() -> markPlayerPlacedKeep(level, pos));
                    }
                }
                case CONVERT -> {
                    if (CommonConfig.AUTOMATIC_CONVERSION.get()) {
                        level.getServer().execute(() -> state(level).queuePosition(pos, Cause.PLAYER_PLACED));
                    }
                }
            }
        } else if (CommonConfig.AUTOMATIC_CONVERSION.get() && CommonConfig.CONVERT_RUNTIME_PLACED_SPAWNERS.get()) {
            level.getServer().execute(() -> state(level).queuePosition(pos, Cause.AUTOMATIC));
        }
    }

    private static boolean markPlayerPlacedKeep(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
            spawner.getPersistentData().putBoolean(PLAYER_KEEP_MARKER, true);
            spawner.setChanged();
            return true;
        }
        return false;
    }

    public static ConversionResult convertAt(ServerLevel level, BlockPos pos, Cause cause) {
        if (!level.getBlockState(pos).is(Blocks.SPAWNER)) {
            return ConversionResult.skipped("no spawner block at " + pos.toShortString());
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof SpawnerBlockEntity spawnerBlockEntity)) {
            return ConversionResult.skipped("spawner block entity is unavailable at " + pos.toShortString());
        }
        if (DormantSpawnerData.isDormant(spawnerBlockEntity)) {
            return ConversionResult.skipped("spawner is dormant until its Living Spawner respawn deadline");
        }
        if (cause != Cause.COMMAND && spawnerBlockEntity.getPersistentData().getBoolean(PLAYER_KEEP_MARKER)) {
            return ConversionResult.skipped("player-placed spawner is configured to remain a block");
        }

        List<LivingSpawnerEntity> existing = level.getEntitiesOfClass(
            LivingSpawnerEntity.class,
            new AABB(pos).inflate(SpawnerExposure.MAX_SEARCH_RADIUS + 1.0),
            entity -> entity.conversionSourcePos().equals(pos) && entity.isAlive()
        );
        if (!existing.isEmpty()) {
            if (level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)) {
                return ConversionResult.success("recovered an interrupted conversion at " + pos.toShortString());
            }
            return ConversionResult.skipped("a Living Spawner already occupies " + pos.toShortString());
        }

        List<String> decodeErrors = new ArrayList<>();
        CompoundTag vanillaState;
        try {
            vanillaState = spawnerBlockEntity.getSpawner().save(new CompoundTag());
        } catch (RuntimeException exception) {
            warnConversionFailure(level, pos, "Failed to serialize spawner", exception);
            return ConversionResult.skipped("could not serialize spawner data");
        }
        SpawnerState state = SpawnerState.decode(vanillaState, decodeErrors::add).orElse(null);
        if (state == null) {
            if (!decodeErrors.isEmpty()) {
                warnConversionFailure(level, pos, "Skipping invalid spawner: " + String.join("; ", decodeErrors), null);
            }
            return ConversionResult.skipped("spawner has no usable entity data");
        }
        SpawnerEligibility.Result eligibility = SpawnerEligibility.evaluate(level, state);
        if (!eligibility.eligible()) {
            return ConversionResult.skipped(eligibility.reason());
        }
        if (cause != Cause.COMMAND && !passesStoredChance(level, spawnerBlockEntity)) {
            return ConversionResult.skipped("stored conversion-chance result is false");
        }

        BlockPos destination = pos;
        if (CommonConfig.RELOCATE_ENCASED_SPAWNERS.get()) {
            Set<Block> exposureBlocks = SpawnerExposure.configuredBlocks();
            if (exposureBlocks.isEmpty()) {
                return ConversionResult.skipped("no configured exposure block IDs are registered");
            }
            int minimumSides = CommonConfig.MINIMUM_EXPOSED_SIDES.get();
            if (!SpawnerExposure.hasRequiredExposure(level, pos, exposureBlocks, minimumSides)) {
                destination = SpawnerExposure.findDestination(
                    level,
                    pos,
                    ModEntities.LIVING_SPAWNER.get(),
                    exposureBlocks,
                    minimumSides,
                    CommonConfig.RELOCATION_SEARCH_RADIUS.get()
                ).orElse(null);
                if (destination == null) {
                    return ConversionResult.skipped(
                        "encased spawner has no valid exposed destination within "
                            + CommonConfig.RELOCATION_SEARCH_RADIUS.get()
                            + " blocks"
                    );
                }
            }
        }

        LivingSpawnerEntity entity = ModEntities.LIVING_SPAWNER.get().create(level);
        if (entity == null) {
            FoughtNotFarmed.LOGGER.error("NeoForge returned null while creating a Living Spawner for {} in {}", pos, level.dimension().location());
            return ConversionResult.skipped("Living Spawner entity could not be created");
        }
        entity.initialize(destination, pos, state);

        boolean added;
        try {
            added = level.addFreshEntity(entity);
        } catch (RuntimeException exception) {
            warnConversionFailure(level, pos, "Failed to add Living Spawner", exception);
            return ConversionResult.skipped("Living Spawner entity addition threw an exception");
        }
        if (!added) {
            return ConversionResult.skipped("Living Spawner entity addition was rejected");
        }

        // Add-first minimizes lost encounters. A later pass recognizes the entity by sourcePos and
        // removes a block left behind by an interrupted save, preventing duplication.
        if (!level.getBlockState(pos).is(Blocks.SPAWNER) || level.getBlockEntity(pos) != spawnerBlockEntity) {
            entity.discard();
            return ConversionResult.skipped("spawner changed while conversion was in progress");
        }
        if (!level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL) || !level.getBlockState(pos).isAir()) {
            entity.discard();
            return ConversionResult.skipped("could not remove original spawner block");
        }
        if (destination.equals(pos)) {
            FoughtNotFarmed.LOGGER.debug("Converted spawner at {} in {}", pos, level.dimension().location());
            return ConversionResult.success("converted spawner at " + pos.toShortString());
        }
        FoughtNotFarmed.LOGGER.debug(
            "Converted encased spawner at {} and relocated it to {} in {}",
            pos,
            destination,
            level.dimension().location()
        );
        return ConversionResult.success(
            "converted encased spawner at " + pos.toShortString() + " and relocated it to " + destination.toShortString()
        );
    }

    private static boolean passesStoredChance(ServerLevel level, SpawnerBlockEntity blockEntity) {
        double chance = CommonConfig.CONVERSION_CHANCE.get();
        CompoundTag persistentData = blockEntity.getPersistentData();
        if (persistentData.getBoolean(CHANCE_ROLLED_MARKER)) {
            return persistentData.getBoolean(CHANCE_RESULT_MARKER);
        }
        boolean result = chance >= 1.0 || chance > 0.0 && level.random.nextDouble() < chance;
        persistentData.putBoolean(CHANCE_ROLLED_MARKER, true);
        persistentData.putBoolean(CHANCE_RESULT_MARKER, result);
        blockEntity.setChanged();
        return result;
    }

    private static void warnConversionFailure(ServerLevel level, BlockPos pos, String message, RuntimeException exception) {
        if (!state(level).shouldLogFailure(pos, level.getGameTime())) {
            return;
        }
        if (exception == null) {
            FoughtNotFarmed.LOGGER.warn("{} at {} in {}", message, pos, level.dimension().location());
        } else {
            FoughtNotFarmed.LOGGER.warn("{} at {} in {}", message, pos, level.dimension().location(), exception);
        }
    }

    public static ScanResult scanChunkNow(ServerLevel level, LevelChunk chunk, Cause cause) {
        int found = 0;
        int converted = 0;
        for (BlockPos pos : Set.copyOf(chunk.getBlockEntitiesPos())) {
            if (!level.getBlockState(pos).is(Blocks.SPAWNER)) {
                continue;
            }
            found++;
            if (convertAt(level, pos, cause).converted()) {
                converted++;
            }
        }
        return new ScanResult(found, converted);
    }

    private static LevelScanState state(ServerLevel level) {
        return LEVEL_STATES.computeIfAbsent(level, ignored -> new LevelScanState());
    }

    private record QueuedPosition(BlockPos pos, Cause cause) {
    }

    private static final class LevelScanState {
        private final Set<Long> loadedChunks = new LinkedHashSet<>();
        private final ArrayDeque<Long> runtimeOrder = new ArrayDeque<>();
        private final ArrayDeque<Long> initialScans = new ArrayDeque<>();
        private final Set<Long> queuedInitialScans = new HashSet<>();
        private final ArrayDeque<QueuedPosition> queuedPositions = new ArrayDeque<>();
        private final LinkedHashMap<BlockPos, Long> failureLogTimes = new LinkedHashMap<>();
        private long nextRuntimeScan;

        void trackChunk(long chunkKey) {
            if (this.loadedChunks.add(chunkKey)) {
                this.runtimeOrder.addLast(chunkKey);
            }
        }

        void untrackChunk(long chunkKey) {
            this.loadedChunks.remove(chunkKey);
            this.queuedInitialScans.remove(chunkKey);
        }

        void queueInitialScan(long chunkKey) {
            if (this.queuedInitialScans.add(chunkKey)) {
                this.initialScans.addLast(chunkKey);
            }
        }

        void queuePosition(BlockPos pos, Cause cause) {
            this.queuedPositions.addLast(new QueuedPosition(pos.immutable(), cause));
        }

        boolean shouldLogFailure(BlockPos pos, long gameTime) {
            Long previous = this.failureLogTimes.get(pos);
            if (previous != null && gameTime - previous < 1200) {
                return false;
            }
            this.failureLogTimes.put(pos.immutable(), gameTime);
            if (this.failureLogTimes.size() > 1024) {
                this.failureLogTimes.remove(this.failureLogTimes.keySet().iterator().next());
            }
            return true;
        }

        void processQueuedPositions(ServerLevel level) {
            for (int i = 0; i < POSITIONS_PER_TICK && !this.queuedPositions.isEmpty(); i++) {
                QueuedPosition queued = this.queuedPositions.removeFirst();
                if (level.getChunkSource().getChunkNow(queued.pos().getX() >> 4, queued.pos().getZ() >> 4) != null) {
                    convertAt(level, queued.pos(), queued.cause());
                }
            }
        }

        void processInitialScans(ServerLevel level) {
            for (int i = 0; i < INITIAL_SCANS_PER_TICK && !this.initialScans.isEmpty(); i++) {
                long key = this.initialScans.removeFirst();
                this.queuedInitialScans.remove(key);
                if (!this.loadedChunks.contains(key)) {
                    continue;
                }
                int chunkX = net.minecraft.world.level.ChunkPos.getX(key);
                int chunkZ = net.minecraft.world.level.ChunkPos.getZ(key);
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    this.queueInitialScan(key);
                    continue;
                }
                scanChunkNow(level, chunk, Cause.AUTOMATIC);
            }
        }

        void processRuntimeScans(ServerLevel level) {
            if (!CommonConfig.AUTOMATIC_CONVERSION.get() || !CommonConfig.CONVERT_RUNTIME_PLACED_SPAWNERS.get()) {
                return;
            }
            long gameTime = level.getGameTime();
            if (gameTime < this.nextRuntimeScan) {
                return;
            }
            this.nextRuntimeScan = gameTime + CommonConfig.RUNTIME_SCAN_INTERVAL_TICKS.get();
            int budget = Math.min(CommonConfig.RUNTIME_CHUNKS_PER_SCAN.get(), this.runtimeOrder.size());
            for (int i = 0; i < budget; i++) {
                long key = this.runtimeOrder.removeFirst();
                if (!this.loadedChunks.contains(key)) {
                    continue;
                }
                this.runtimeOrder.addLast(key);
                LevelChunk chunk = level.getChunkSource().getChunkNow(
                    net.minecraft.world.level.ChunkPos.getX(key),
                    net.minecraft.world.level.ChunkPos.getZ(key)
                );
                if (chunk != null) {
                    scanChunkNow(level, chunk, Cause.AUTOMATIC);
                }
            }
        }
    }
}
