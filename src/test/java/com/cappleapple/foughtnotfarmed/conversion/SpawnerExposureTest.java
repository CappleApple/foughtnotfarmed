package com.cappleapple.foughtnotfarmed.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class SpawnerExposureTest {
    @Test
    void nearestShellWins() {
        BlockPos origin = BlockPos.ZERO;
        Set<BlockPos> candidates = Set.of(new BlockPos(3, 0, 0), new BlockPos(-1, 0, 0));

        assertEquals(
            new BlockPos(-1, 0, 0),
            SpawnerExposure.findNearest(origin, 4, candidates::contains).orElseThrow()
        );
    }

    @Test
    void euclideanDistanceBreaksTiesWithinShell() {
        BlockPos origin = BlockPos.ZERO;
        Set<BlockPos> candidates = Set.of(new BlockPos(-2, -2, -2), new BlockPos(2, 0, 0));

        assertEquals(
            new BlockPos(2, 0, 0),
            SpawnerExposure.findNearest(origin, 2, candidates::contains).orElseThrow()
        );
    }

    @Test
    void originAndPositionsBeyondTheBoundAreNotConsidered() {
        BlockPos origin = BlockPos.ZERO;
        Set<BlockPos> candidates = Set.of(origin, new BlockPos(3, 0, 0));

        assertTrue(SpawnerExposure.findNearest(origin, 2, candidates::contains).isEmpty());
    }
}
