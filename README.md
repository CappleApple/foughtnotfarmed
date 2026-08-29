# Fought Not Farmed

Fought Not Farmed turns ordinary mob spawner blocks into stationary, attackable Living Spawners. A Living Spawner keeps summoning its configured enemies while a player is nearby, so the encounter ends only when players fight through the reinforcements and destroy the cage itself.

**Before**

`Spawner block → torch it or farm it`

**After**

`Living Spawner → fight through its summons → destroy it`

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.244 or newer for Minecraft 1.21.1
- Java 21 on servers and development machines
- The mod must be installed on both the server and connecting clients

No library mods are required. Fought Not Farmed does not replace Minecraft's terrain or structure-generation systems. Its mixins are limited to the dormant-spawner tick guard and scoped vanilla light-rule hooks that activate only during a Living Spawner candidate check.

## Installation

1. Install NeoForge for Minecraft 1.21.1.
2. Place `foughtnotfarmed-1.3.2.jar` in the instance's `mods` directory.
3. Install the same JAR on the dedicated server and every client.
4. Start the game or server once to generate the configuration files.

## Default behavior

- Every eligible `minecraft:spawner` has a 100% one-time conversion chance.
- Encased spawners relocate to the nearest loaded air or water position with at least two exposed faces; the behavior, accepted blocks, face count, and search radius are configurable.
- New structures, old loaded chunks, runtime block placement, and bounded round-robin scans are supported.
- The original `SpawnData`, weighted `SpawnPotentials`, entity NBT, delays, spawn count, nearby cap, player range, and spawn range are retained.
- The Living Spawner has 50 health, one-block dimensions, no movement, no knockback, and no pathfinding goals.
- It accepts normal melee, projectile, magic, fire, and explosion damage. In-wall, drowning, fall, cactus, cramming, freezing, and similar environmental damage are disabled by default.
- It pauses outside the configured player range and on Peaceful difficulty.
- It drops 15 XP when killed by a player and no item loot by default.
- Enemies already summoned remain alive after the Living Spawner is destroyed.
- A destroyed Living Spawner returns at its death position after 30 minutes of loaded server time by default. Respawning, the delay, server-time versus system-time tracking, and health-based delay scaling are configurable.
- Optionally, defeat can leave a deactivated vanilla spawner block containing the preserved mob data. It can be mined by compatible spawner-mining mods, or it turns back into the Living Spawner when its respawn timer expires.
- The cage uses Minecraft's spawner block model and renders the current mob preview with any available modded entity renderer.
- Active cages emit vanilla smoke and flame particles. Blocked spawn attempts keep blue flames without warning sounds or shake. Once a valid location is found, the cage plays its configurable warning, spawns, then sounds an alert and briefly grows and shrinks on success.
- Living Spawners ignore block-emitted light and sky light by default. Each light channel can be restored independently in the server config; collision and other spawn rules still apply.

## Generic compatibility

The spawner's existing data is the compatibility layer. If another mod or datapack places a normal Minecraft spawner containing a registered entity type, Fought Not Farmed attempts to load that entity from its original NBT and fires NeoForge's spawner position/finalize-spawn hooks. There is no hardcoded list of supported mobs or structures.

The conversion system reacts after chunks reach normal server-thread processing. It examines block-entity positions rather than scanning every block, never scans the whole world, and does not wait on chunk-generation worker threads. Runtime scans rotate through a configurable number of loaded chunks at a configurable interval.

Datapacks can extend these entity-type tags:

- `foughtnotfarmed:cannot_be_spawned_by_living_spawner` — hard exclusion; includes the Living Spawner itself.
- `foughtnotfarmed:bosses` — excluded while `allowBossEntities` is false; includes the Ender Dragon and Wither by default.

## Configuration

Gameplay settings are written to `config/foughtnotfarmed-common.toml`. Presentation settings are written to `config/foughtnotfarmed-client.toml`. Restart the game/server after changing them; V1 deliberately does not expose an unsafe partial config reload command.

Important groups include:

- `conversion`: automatic conversion, one-time chance, old/runtime chunk handling, player placement policy, entity/dimension filters, scan budgets, and encased-spawner relocation.
- `combat`: health/scaling, armor, knockback resistance, damage categories, and global damage multiplier.
- `respawning`: enablement, delay, clock source, optional maximum-health scaling, and the dormant vanilla-spawner phase.
- `spawning`: spawn-count/delay/range multipliers, active-summon policy, line of sight, boss policy, Peaceful behavior, independent block/sky light handling, and warning timing.
- `rewards`: XP, loot table, and whether loaded tracked summons are discarded on death.
- client config: activation particles, cage shake, preview rotation, and visual hover amount.

Filter semantics are explicit: in `BLACKLIST` mode only the blacklist is consulted; in `WHITELIST` mode only the whitelist is consulted. Every possible entity ID in a weighted spawner must pass the active filter before the spawner converts.

When conversion chance is below 100%, the first result is saved in the vanilla spawner block entity. Reloading the chunk does not reroll until it eventually converts. An administrator can still explicitly convert it with a command.

### Player-placed spawners

`playerPlacedMode` supports `CONVERT`, `KEEP`, and `DISALLOW`. Placement is reliably identified only when this mod observes a player place the block. A kept placement receives a persistent marker. Minecraft does not store trustworthy natural-versus-player provenance on old spawner blocks, so existing player-placed spawners from before installation cannot be distinguished from generated ones; they follow existing-chunk policy.

## Commands

All commands require permission level 2.

- `/foughtnotfarmed convert` — convert the spawner block being looked at, bypassing automatic enable/chance and a saved player-keep marker while retaining entity/dimension safety filters.
- `/foughtnotfarmed convertchunk` — convert eligible spawner block entities in the current loaded chunk.
- `/foughtnotfarmed convertarea <radius>` — inspect block entities in currently loaded chunks intersecting a block radius up to 256; it does not force-load chunks.
- `/foughtnotfarmed debug` — show the looked-at Living Spawner's source position, UUID, entity pool, delay, ranges, and owned active-summon count.

## Existing worlds

The mod can be added to an existing world. With `convertExistingChunks=true`, eligible spawners are converted as their chunks load. No new world or structure-specific integration is required.

Back up important worlds before changing a mod list. Removing the mod removes Living Spawner entities; it cannot recreate the block that each entity replaced.

## Intentional safety limits and differences

- Malformed values are bounded to protect the server: spawn count 64, active tracking/cap 256, player range 256, and spawn range 64.
- The owner UUID is stored in each spawned entity's NeoForge persistent data. Loaded tracked entities count toward the cap; unloaded entities do not consume active processing or an active slot until encountered again.
- A Living Spawner tracks at most 512 UUIDs, prunes dead/unloaded references, and rebuilds ownership from nearby loaded entities after reload.
- If `despawnSummonsOnDeath=true`, currently loaded tracked summons are discarded. Unloaded entities cannot be removed without force-loading chunks, which the mod intentionally avoids.
- Respawn records are stored per dimension. A due respawn waits until its death chunk is loaded and never force-loads that chunk.
- Programmatic conversion uses ordinary server block mutation. Damage remains a normal living-entity event that protection mods can cancel; command access remains permission-gated.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for implementation details and [docs/TESTING.md](docs/TESTING.md) for the acceptance checklist.

See [configuration.md](configuration.md) for every common and client option, exact scaling behavior, and copy-ready examples.

## Building

```powershell
$env:JAVA_HOME = 'path-to-java-21'
.\gradlew.bat test build
```

The release JAR is produced under `build/libs/`.

## License

Fought Not Farmed is available under the [MIT License](LICENSE). It is an independent project and is not affiliated with Mojang Studios. Its combat-objective concept is inspired by dungeon-crawler spawner encounters; it ships no Minecraft Dungeons assets.
