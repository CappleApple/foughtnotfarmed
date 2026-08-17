package com.cappleapple.foughtnotfarmed.conversion;

import com.cappleapple.foughtnotfarmed.config.CommonConfig;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Bounded, loaded-chunk-only exposure checks for Living Spawner placement. */
public final class SpawnerExposure {
    public static final int MAX_SEARCH_RADIUS = 16;

    private SpawnerExposure() {
    }

    public static Set<Block> configuredBlocks() {
        Set<Block> blocks = new LinkedHashSet<>();
        for (String configuredId : CommonConfig.EXPOSURE_BLOCKS.get()) {
            ResourceLocation id = ResourceLocation.tryParse(configuredId);
            if (id != null) {
                BuiltInRegistries.BLOCK.getOptional(id).ifPresent(blocks::add);
            }
        }
        return Set.copyOf(blocks);
    }

    public static boolean hasRequiredExposure(
        ServerLevel level,
        BlockPos position,
        Set<Block> exposureBlocks,
        int minimumSides
    ) {
        return countExposedSides(level, position, null, exposureBlocks) >= minimumSides;
    }

    public static Optional<BlockPos> findDestination(
        ServerLevel level,
        BlockPos origin,
        EntityType<?> entityType,
        Set<Block> exposureBlocks,
        int minimumSides,
        int searchRadius
    ) {
        int boundedRadius = Math.min(MAX_SEARCH_RADIUS, Math.max(1, searchRadius));
        return findNearest(origin, boundedRadius, candidate -> {
            if (level.isOutsideBuildHeight(candidate)
                || !isLoaded(level, candidate)
                || !level.getWorldBorder().isWithinBounds(candidate)
                || !exposureBlocks.contains(level.getBlockState(candidate).getBlock())) {
                return false;
            }
            if (countExposedSides(level, candidate, origin, exposureBlocks) < minimumSides) {
                return false;
            }
            return level.noCollision(entityType.getSpawnAABB(
                candidate.getX() + 0.5,
                candidate.getY(),
                candidate.getZ() + 0.5
            ));
        });
    }

    static Optional<BlockPos> findNearest(BlockPos origin, int searchRadius, Predicate<BlockPos> isCandidate) {
        for (int radius = 1; radius <= searchRadius; radius++) {
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;
            for (int xOffset = -radius; xOffset <= radius; xOffset++) {
                for (int yOffset = -radius; yOffset <= radius; yOffset++) {
                    for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                        if (Math.max(Math.max(Math.abs(xOffset), Math.abs(yOffset)), Math.abs(zOffset)) != radius) {
                            continue;
                        }
                        BlockPos candidate = origin.offset(xOffset, yOffset, zOffset);
                        if (!isCandidate.test(candidate)) {
                            continue;
                        }
                        double distance = origin.distSqr(candidate);
                        if (distance < bestDistance) {
                            best = candidate.immutable();
                            bestDistance = distance;
                        }
                    }
                }
            }
            if (best != null) {
                return Optional.of(best);
            }
        }
        return Optional.empty();
    }

    private static int countExposedSides(
        ServerLevel level,
        BlockPos position,
        BlockPos originThatBecomesAir,
        Set<Block> exposureBlocks
    ) {
        int exposed = 0;
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = position.relative(direction);
            if (originThatBecomesAir != null && neighbor.equals(originThatBecomesAir)) {
                if (exposureBlocks.contains(Blocks.AIR)) {
                    exposed++;
                }
            } else if (isLoaded(level, neighbor) && exposureBlocks.contains(level.getBlockState(neighbor).getBlock())) {
                exposed++;
            }
        }
        return exposed;
    }

    private static boolean isLoaded(ServerLevel level, BlockPos position) {
        return level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) != null;
    }
}
