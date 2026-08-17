package com.cappleapple.foughtnotfarmed.spawner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cappleapple.foughtnotfarmed.config.CommonConfig.FilterMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpawnerEligibilityTest {
    @Test
    void blacklistRejectsOnlyListedIds() {
        assertFalse(SpawnerEligibility.matchesFilter("minecraft:wither", FilterMode.BLACKLIST, List.of(), List.of("minecraft:wither")));
        assertTrue(SpawnerEligibility.matchesFilter("minecraft:zombie", FilterMode.BLACKLIST, List.of(), List.of("minecraft:wither")));
    }

    @Test
    void whitelistAcceptsOnlyListedIds() {
        assertTrue(SpawnerEligibility.matchesFilter("example:goblin", FilterMode.WHITELIST, List.of("example:goblin"), List.of()));
        assertFalse(SpawnerEligibility.matchesFilter("minecraft:zombie", FilterMode.WHITELIST, List.of("example:goblin"), List.of()));
    }

    @Test
    void whitelistTakesPrecedenceOverBlacklistListInWhitelistMode() {
        assertTrue(SpawnerEligibility.matchesFilter("example:goblin", FilterMode.WHITELIST, List.of("example:goblin"), List.of("example:goblin")));
    }

    @Test
    void blacklistTakesPrecedenceOverWhitelistListInBlacklistMode() {
        assertFalse(SpawnerEligibility.matchesFilter("example:goblin", FilterMode.BLACKLIST, List.of("example:goblin"), List.of("example:goblin")));
    }
}
