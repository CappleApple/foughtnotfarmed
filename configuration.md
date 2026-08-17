# Fought Not Farmed configuration

Fought Not Farmed creates two TOML configuration files after the mod is launched once:

- `config/foughtnotfarmed-common.toml` controls conversion, combat, respawning, spawning, and rewards. On a multiplayer server, the server's copy is authoritative.
- `config/foughtnotfarmed-client.toml` controls visuals for one client. Players may use different client settings without affecting gameplay.

Stop the game or server before editing these files, then restart it after saving. The mod does not provide a live-reload command.

NeoForge preserves values already written to an existing config. After upgrading from 1.1, an existing explicit `baseHealth = 36.0` remains 36 until you change it to `50.0`; newly generated configs use the new 50-health default.

TOML section names appear in square brackets, strings and enum values use quotes, booleans are `true` or `false`, and lists use square brackets:

```toml
[conversion]
enabled = true
entityBlacklist = ["minecraft:creeper", "examplemod:dangerous_mob"]
```

Resource IDs must use the `namespace:path` form. Minecraft content uses the `minecraft` namespace; modded content uses that mod's namespace.

## Common configuration

### Conversion

The `[conversion]` section decides which vanilla spawner blocks become Living Spawner entities and how the mod finds them.

| Option | Default | Valid values | Effect |
| --- | ---: | --- | --- |
| `enabled` | `true` | `true`, `false` | Enables automatic conversion. Setting this to `false` stops chunk-load, runtime, and observed player-placement conversion. Administrator conversion commands remain available. |
| `conversionChance` | `1.0` | `0.0` to `1.0` | Probability that an otherwise eligible spawner converts automatically. `1.0` is 100%, `0.25` is 25%, and `0.0` disables automatic conversion through chance. |
| `convertExistingChunks` | `true` | `true`, `false` | Scans spawner block entities when already-generated chunks load. New chunks can still be scanned when automatic conversion is enabled. |
| `convertRuntimePlacedSpawners` | `true` | `true`, `false` | Periodically scans loaded chunks for spawners created after chunk load by commands, scripts, structure blocks, or other mods. It also queues non-player placement events. |
| `playerPlacedMode` | `"CONVERT"` | `"CONVERT"`, `"KEEP"`, `"DISALLOW"` | Controls a spawner placement that the mod directly observes as being performed by a player. See the detailed behavior below. |
| `entityFilterMode` | `"BLACKLIST"` | `"BLACKLIST"`, `"WHITELIST"` | Selects which entity list is active. The inactive list is ignored. |
| `entityWhitelist` | `[]` | List of entity IDs | In `WHITELIST` mode, every entity type in a spawner's current and weighted entries must appear in this list. |
| `entityBlacklist` | `[]` | List of entity IDs | In `BLACKLIST` mode, conversion is rejected if any current or weighted entry appears in this list. |
| `dimensionFilterMode` | `"BLACKLIST"` | `"BLACKLIST"`, `"WHITELIST"` | Selects which dimension list is active. The inactive list is ignored. |
| `dimensionWhitelist` | `[]` | List of dimension IDs | In `WHITELIST` mode, conversion is allowed only in listed dimensions. |
| `dimensionBlacklist` | `[]` | List of dimension IDs | In `BLACKLIST` mode, conversion is allowed everywhere except listed dimensions. |
| `runtimeScanIntervalTicks` | `100` | `20` to `1200` | Game ticks between runtime scan batches. There are normally 20 ticks per second, so `100` is about five seconds. |
| `runtimeChunksPerScan` | `8` | `1` to `128` | Maximum loaded chunks inspected in each runtime batch. Only known block-entity positions are checked; the mod does not scan every block. |
| `relocateEncasedSpawners` | `true` | `true`, `false` | When enabled, an encased spawner is anchored at the nearest valid exposed position found by the bounded search. |
| `exposureBlocks` | Air variants and water | List of block IDs | Exact block IDs that count as exposed faces and may contain a relocated Living Spawner. |
| `minimumExposedSides` | `2` | `1` to `6` | Required exposed orthogonal faces: up, down, north, south, east, and west. |
| `relocationSearchRadius` | `8` | `1` to `16` | Maximum Chebyshev distance searched around an encased source. The search does not load chunks. |

#### Conversion chance is a stored one-time decision

An automatic chance result is stored in the vanilla spawner block entity. Unloading and reloading the chunk does not reroll it. This prevents repeatedly loading a chunk until a low-probability conversion succeeds.

For example, this gives every eligible spawner one 20% automatic chance:

```toml
[conversion]
enabled = true
conversionChance = 0.2
```

Changing `conversionChance` later does not clear a result that was already stored. An administrator can explicitly convert the targeted spawner with `/foughtnotfarmed convert`; commands bypass `enabled`, the chance result, and a saved player `KEEP` marker, but still enforce entity, dimension, unsupported-entity, and boss safety checks.

#### Player-placed modes

- `CONVERT` queues an observed player placement for conversion when `enabled=true`.
- `KEEP` leaves the placed block intact and stores a persistent marker so later automatic scans continue to ignore it.
- `DISALLOW` cancels the placement event.

Minecraft does not preserve reliable natural-versus-player provenance for arbitrary old spawner blocks. Spawners placed before this mod observed the placement follow normal existing-chunk rules.

Example that preserves newly player-placed spawners while converting generated spawners:

```toml
[conversion]
enabled = true
convertExistingChunks = true
playerPlacedMode = "KEEP"
```

#### Entity and dimension filters

Filters are exact resource-ID matches; they are not tags, wildcards, or regular expressions. Only the list selected by its mode is consulted:

- `BLACKLIST` allows everything except IDs in the blacklist. An empty blacklist allows everything that passes the other safety checks.
- `WHITELIST` allows only IDs in the whitelist. An empty whitelist allows nothing.

A weighted spawner is eligible only when every possible entity type passes the active entity filter. This prevents a disallowed mob from appearing later when the spawner chooses another weighted entry.

Example allowing only zombie and skeleton spawners in the Overworld:

```toml
[conversion]
entityFilterMode = "WHITELIST"
entityWhitelist = ["minecraft:zombie", "minecraft:skeleton"]
entityBlacklist = []

dimensionFilterMode = "WHITELIST"
dimensionWhitelist = ["minecraft:overworld"]
dimensionBlacklist = []
```

Example allowing all eligible entities but excluding the Nether, the End, and Creepers:

```toml
[conversion]
entityFilterMode = "BLACKLIST"
entityBlacklist = ["minecraft:creeper"]

dimensionFilterMode = "BLACKLIST"
dimensionBlacklist = ["minecraft:the_nether", "minecraft:the_end"]
```

The filters do not override the `foughtnotfarmed:cannot_be_spawned_by_living_spawner` entity-type tag. Bosses in `foughtnotfarmed:bosses` also remain excluded unless `allowBossEntities=true` under `[spawning]`.

#### Runtime scan budgeting

Runtime scans rotate through loaded chunks. With the defaults, eight chunks are inspected about every five seconds. If 80 chunks remain loaded, one complete rotation takes roughly 50 seconds: `ceil(80 / 8) × 5`.

Lower-impact example for a server with many loaded chunks:

```toml
[conversion]
convertRuntimePlacedSpawners = true
runtimeScanIntervalTicks = 200
runtimeChunksPerScan = 4
```

This checks four loaded chunks about every ten seconds. Chunk-load scans and directly observed placements use their own bounded queues and are not controlled by these two runtime-batch values.

#### Encased-spawner relocation

With relocation enabled, conversion first counts the six blocks touching the original spawner. If at least `minimumExposedSides` of them are in `exposureBlocks`, the Living Spawner stays at the original position.

Otherwise, the mod searches outward for the nearest valid destination. A destination must:

- Be within `relocationSearchRadius` and an already-loaded chunk.
- Be inside the world border and build height.
- Occupy a block listed in `exposureBlocks`.
- Have at least `minimumExposedSides` neighboring blocks from the same list.
- Have a collision-free one-block space for the Living Spawner entity.

The search is deterministic and bounded. It does not carve blocks, overwrite the destination block, or force-load chunks. If no valid destination exists, conversion is skipped and the original spawner block remains intact rather than creating an unreachable Living Spawner.

The defaults count all vanilla air variants and water:

```toml
[conversion]
relocateEncasedSpawners = true
exposureBlocks = ["minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:water"]
minimumExposedSides = 2
relocationSearchRadius = 8
```

The IDs are exact blocks, not fluid tags or block tags. A configured destination block must also have no collision shape, so adding a solid block such as `minecraft:stone` will not make solid stone a valid entity space. Unknown but syntactically valid IDs are ignored; if none of the configured IDs are registered, an encased conversion is safely skipped.

To keep every conversion at the original block regardless of surrounding terrain:

```toml
[conversion]
relocateEncasedSpawners = false
```

To require a more open location while allowing only air:

```toml
[conversion]
relocateEncasedSpawners = true
exposureBlocks = ["minecraft:air", "minecraft:cave_air"]
minimumExposedSides = 4
relocationSearchRadius = 12
```

### Combat

The `[combat]` section controls Living Spawner durability and accepted damage.

| Option | Default | Valid values | Effect |
| --- | ---: | --- | --- |
| `baseHealth` | `50.0` | `1.0` to `1024.0` | Starting point for maximum-health calculation. Two health points equal one displayed heart. |
| `healthMode` | `"FIXED"` | `"FIXED"`, `"SPAWN_COUNT"`, `"DIFFICULTY"`, `"COMBINED"` | Chooses which factors are applied to `baseHealth`. Formulas are below. |
| `healthMultiplier` | `1.0` | `0.05` to `100.0` | Final multiplier applied after the selected health-mode factors. |
| `maxScaledHealth` | `200.0` | `1.0` to `1024.0` | Hard upper clamp on calculated maximum health. The final value is also clamped to at least `1.0`. |
| `armor` | `0.0` | `0.0` to `30.0` | Vanilla armor value used during ordinary damage calculation. Higher values reduce applicable damage using Minecraft's armor rules. |
| `knockbackResistance` | `1.0` | `0.0` to `1.0` | Sets the entity attribute for compatibility. Living Spawners are hard-stationary and override movement/knockback, so lowering this does not make the cage movable. |
| `explosionDamage` | `true` | `true`, `false` | Whether explosion-tagged damage may hurt the Living Spawner. |
| `projectileDamage` | `true` | `true`, `false` | Whether projectile-tagged damage may hurt it. |
| `magicDamage` | `true` | `true`, `false` | Whether magic, indirect magic, and dragon-breath damage may hurt it. |
| `fireDamage` | `true` | `true`, `false` | Whether fire-tagged damage may hurt it. |
| `environmentalDamage` | `false` | `true`, `false` | Whether in-wall, cramming, drowning, starvation, cactus, fall, collision, drying, berry-bush, freezing, stalagmite, falling-block/anvil/stalactite, and outside-border damage may hurt it. |
| `damageMultiplier` | `1.0` | `0.0` to `100.0` | Multiplies accepted incoming damage before normal living-entity mitigation. `0.5` halves it, `2.0` doubles it, and `0.0` prevents ordinary accepted damage. |

Damage types tagged to bypass invulnerability are not blocked by the category toggles. Protection mods may still cancel or alter ordinary living-entity damage events.

#### Health formulas

The original spawner's saved `SpawnCount` is used for health scaling; `spawnCountMultiplier` does not change this health input.

```text
spawn factor      = max(1, original SpawnCount / 4)
difficulty factor = Peaceful 0.75, Easy 0.90, Normal 1.00, Hard 1.25

FIXED       = baseHealth
SPAWN_COUNT = baseHealth × spawn factor
DIFFICULTY  = baseHealth × difficulty factor
COMBINED    = baseHealth × spawn factor × difficulty factor

final health = clamp(selected value × healthMultiplier, 1, maxScaledHealth)
```

With `baseHealth=40`, `healthMode="COMBINED"`, and `healthMultiplier=1.5`, a spawner with `SpawnCount=8` on Hard gets:

```text
40 × 2.0 × 1.25 × 1.5 = 150 health
```

If `maxScaledHealth=120`, the result is capped at 120.

Health attributes are calculated when a spawner converts and when an existing Living Spawner loads. Changing world difficulty does not continuously rescale an already loaded cage. On reload, a lower new maximum clamps its current health; a higher new maximum does not automatically heal an existing cage to full.

Example for a tougher cage that must be fought at close range:

```toml
[combat]
baseHealth = 40.0
healthMode = "COMBINED"
healthMultiplier = 1.25
maxScaledHealth = 160.0
armor = 6.0
projectileDamage = false
damageMultiplier = 1.0
```

Example for a fragile, fully damageable cage:

```toml
[combat]
baseHealth = 16.0
healthMode = "FIXED"
healthMultiplier = 1.0
maxScaledHealth = 16.0
armor = 0.0
explosionDamage = true
projectileDamage = true
magicDamage = true
fireDamage = true
environmentalDamage = true
damageMultiplier = 1.0
```

### Respawning

The `[respawning]` section controls whether a destroyed Living Spawner returns and how its persistent timer advances.

| Option | Default | Valid values | Effect |
| --- | ---: | --- | --- |
| `enabled` | `true` | `true`, `false` | When enabled, death schedules a replacement Living Spawner at the block position where it died. When disabled, new deaths are permanent. |
| `delayMinutes` | `30.0` | `0.0` to `10080.0` | Base delay in minutes. Fractional values are allowed; `0.5` is 30 seconds and `0.0` respawns on the next once-per-second queue pass. |
| `clock` | `"SERVER_TIME"` | `"SERVER_TIME"`, `"SYSTEM_TIME"` | Selects loaded server ticks or real-world system time. Exact behavior is described below. |
| `scaleDelayWithMaxHealth` | `false` | `true`, `false` | When enabled, multiplies the base delay by the destroyed cage's maximum health divided by 50. |

The clock mode is saved into each respawn record when the Living Spawner dies. Changing `clock` later applies only to deaths scheduled afterward.

- `SERVER_TIME` counts the dimension's saved game ticks. It advances while the server is running and pauses while the server is stopped. This is the default.
- `SYSTEM_TIME` stores a wall-clock deadline. Time while the server is stopped counts toward the delay; an overdue cage returns after its dimension and death chunk load.

Respawn records preserve the complete current spawner state, including weighted entries and custom entity NBT. A replacement returns at full configured health at its death anchor, even if that location is encased. Respawning does not rerun conversion relocation because doing so would move the cage away from where it died.

The queue never force-loads a death chunk. If the timer becomes due while that chunk is unloaded, the record waits until the chunk is loaded. If another living cage already occupies the death anchor, the pending record is removed instead of making a duplicate. Turning `enabled` off pauses processing of already-pending records without deleting them; if it is enabled again, overdue records can complete.

Default behavior:

```toml
[respawning]
enabled = true
delayMinutes = 30.0
clock = "SERVER_TIME"
scaleDelayWithMaxHealth = false
```

Example that includes offline time and scales tougher cages to take longer:

```toml
[respawning]
enabled = true
delayMinutes = 30.0
clock = "SYSTEM_TIME"
scaleDelayWithMaxHealth = true
```

With scaling enabled, the formula is:

```text
effective delay = delayMinutes × (maximum health / 50)
```

A 50-health cage uses exactly the configured delay, 100 health uses twice the delay, and 25 health uses half. The maximum health captured at death is used, not the cage's remaining health.

### Spawning

The `[spawning]` section modifies the values preserved from each original vanilla or modded spawner.

| Option | Default | Valid values | Effect |
| --- | ---: | --- | --- |
| `spawnCountMultiplier` | `1.0` | `0.0` to `16.0` | Multiplies the original `SpawnCount`, rounds to the nearest integer, and clamps the result from 0 to 64. |
| `maxActiveMode` | `"INHERIT"` | `"INHERIT"`, `"MULTIPLY"`, `"OVERRIDE"` | Chooses how the active owned-summon cap is calculated from the original `MaxNearbyEntities`. |
| `maxActiveMultiplier` | `1.0` | `0.0` to `16.0` | Used only when `maxActiveMode="MULTIPLY"`; the result is rounded and clamped from 0 to 256. |
| `maxActiveOverride` | `6` | `0` to `256` | Used only when `maxActiveMode="OVERRIDE"`. |
| `delayMultiplier` | `1.0` | `0.05` to `100.0` | Multiplies each newly selected delay from the original `MinSpawnDelay`/`MaxSpawnDelay`, rounds it, and clamps it from 1 to 1,200,000 ticks. |
| `playerRangeMode` | `"INHERIT"` | `"INHERIT"`, `"MULTIPLY"`, `"FIXED"` | Chooses how activation distance is calculated from the original `RequiredPlayerRange`. |
| `playerRangeMultiplier` | `1.0` | `0.05` to `16.0` | Used only when `playerRangeMode="MULTIPLY"`; the result is rounded and clamped from 1 to 256 blocks. |
| `fixedPlayerRange` | `16` | `1` to `256` | Used only when `playerRangeMode="FIXED"`. |
| `spawnRangeMultiplier` | `1.0` | `0.0` to `16.0` | Multiplies the original horizontal `SpawnRange`, rounds it, and clamps it from 0 to 64 blocks. |
| `requireLineOfSight` | `false` | `true`, `false` | If enabled, at least one alive, non-spectator player in activation range must have line of sight to the cage. |
| `allowBossEntities` | `false` | `true`, `false` | Permits entity types in the `foughtnotfarmed:bosses` tag. It does not bypass filters, the hard unsupported tag, or normal spawn rules. |
| `activateOnPeaceful` | `false` | `true`, `false` | If disabled, activation and delay countdown pause on Peaceful. If enabled, the timer may run, but normal placement rules still determine whether an entity can actually spawn. |
| `spawnWarningTicks` | `40` | `0` to `1200` | Number of ticks before a spawn attempt when the cage begins shaking and plays one warning sound. `0` disables this warning window. |

#### Counts, caps, and delays

Suppose a vanilla spawner has `SpawnCount=4`, `MaxNearbyEntities=6`, and a delay range of 200–800 ticks:

```toml
[spawning]
spawnCountMultiplier = 1.5
maxActiveMode = "MULTIPLY"
maxActiveMultiplier = 2.0
delayMultiplier = 0.5
```

The Living Spawner attempts up to 6 summons per cycle, permits up to 12 currently loaded owned summons, and chooses roughly half-length delays. A cycle never attempts more entities than the remaining active-cap space. Failed placement attempts use a short 20-tick retry delay.

`maxActiveMode="INHERIT"` ignores both `maxActiveMultiplier` and `maxActiveOverride`. `MULTIPLY` ignores the override. `OVERRIDE` ignores the original cap and multiplier. The same pattern applies to the player-range mode and its two supporting values.

Setting `spawnCountMultiplier=0.0` produces zero spawn attempts. Setting the effective active cap to zero also prevents summons. Setting `spawnRangeMultiplier=0.0` keeps random horizontal positions centered on the cage, though the preserved entity NBT, vertical offset, collision, placement, light, and other spawn rules still apply.

The current saved countdown is retained across saves. `delayMultiplier` is applied when the next delay is selected, not retroactively to a countdown already in progress.

#### Spawn warning and pulse

When an active countdown enters `spawnWarningTicks`, the server plays `minecraft:block.trial_spawner.detect_player` once and synchronizes the warning state so clients shake the cage. With the default `40`, warning begins about two seconds before the attempt. If the player leaves activation range, the warning and countdown pause; entering the warning window again can play a new alert.

After a cycle successfully creates at least one mob, the server broadcasts a short visual event. Clients scale the cage and its preview up by at most 8%, then smoothly return them to normal over 12 ticks. This pulse is visual only: the entity position and hitbox do not change.

Example with a five-second warning:

```toml
[spawning]
spawnWarningTicks = 100
```

Disable the warning sound and pre-spawn shake while retaining the successful-spawn pulse:

```toml
[spawning]
spawnWarningTicks = 0
```

#### Activation examples

Require players to be within 12 blocks and able to see the cage:

```toml
[spawning]
playerRangeMode = "FIXED"
fixedPlayerRange = 12
requireLineOfSight = true
```

Double each spawner's original activation range while preserving differences between spawners:

```toml
[spawning]
playerRangeMode = "MULTIPLY"
playerRangeMultiplier = 2.0
```

Allow boss-tagged spawners only if they also pass an explicit whitelist:

```toml
[conversion]
entityFilterMode = "WHITELIST"
entityWhitelist = ["minecraft:wither"]

[spawning]
allowBossEntities = true
```

All entity IDs in weighted entries must still pass. Enabling bosses does not guarantee a successful spawn if Minecraft or another mod rejects the spawn position.

### Rewards

The `[rewards]` section controls what happens when a Living Spawner dies.

| Option | Default | Valid values | Effect |
| --- | ---: | --- | --- |
| `xpEnabled` | `true` | `true`, `false` | Enables the configured base XP reward through normal living-entity death rules. |
| `xpAmount` | `15` | `0` to `10000` | Base XP reward when XP is enabled. |
| `lootEnabled` | `false` | `true`, `false` | Enables item loot. Loot also requires the world's `doMobLoot` game rule. |
| `lootTable` | `"minecraft:empty"` | Resource ID | Loot table used when item loot is enabled. The config validates ID syntax, not whether that datapack resource exists. |
| `despawnSummonsOnDeath` | `false` | `true`, `false` | Discards currently loaded entities that are tracked as summons of the destroyed Living Spawner. |

Example using a datapack loot table and a larger XP reward:

```toml
[rewards]
xpEnabled = true
xpAmount = 50
lootEnabled = true
lootTable = "mypack:entities/living_spawner"
despawnSummonsOnDeath = true
```

`despawnSummonsOnDeath` deliberately does not force-load chunks. A tracked summon that is unloaded cannot be removed at that moment. With the default `false`, summoned enemies remain and must be defeated normally.

To disable all rewards:

```toml
[rewards]
xpEnabled = false
xpAmount = 0
lootEnabled = false
lootTable = "minecraft:empty"
```

## Client configuration

The client file has no section header. These values affect only presentation on that client and do not need to match the server or other players.

| Option | Default | Valid values | Effect |
| --- | ---: | --- | --- |
| `particleIntensity` | `"FULL"` | `"OFF"`, `"REDUCED"`, `"FULL"` | Controls vanilla-style smoke/flame particles inside active cages, soul-flame warning particles, and smoke damage particles. `REDUCED` emits them less frequently; `OFF` removes them. |
| `cageShake` | `true` | `true`, `false` | Enables the small visual cage shake while recently damaged or preparing to spawn. |
| `previewRotation` | `true` | `true`, `false` | Enables rotation of the entity preview inside the cage. Disabling it leaves the preview visible but stationary. |
| `hoverAmount` | `0.0` | `0.0` to `0.5` | Raises the rendered cage and preview by this many blocks. This is visual only; the entity's hitbox remains aligned with the original spawner position. |

Low-motion, low-particle example:

```toml
particleIntensity = "OFF"
cageShake = false
previewRotation = false
hoverAmount = 0.0
```

More pronounced floating presentation:

```toml
particleIntensity = "FULL"
cageShake = true
previewRotation = true
hoverAmount = 0.2
```

## Complete example presets

### Vanilla-like values with guaranteed conversion

This keeps every original spawner's main timing, count, cap, and range values while replacing all otherwise eligible spawners:

```toml
[conversion]
enabled = true
conversionChance = 1.0
convertExistingChunks = true
convertRuntimePlacedSpawners = true
playerPlacedMode = "CONVERT"
entityFilterMode = "BLACKLIST"
entityWhitelist = []
entityBlacklist = []
dimensionFilterMode = "BLACKLIST"
dimensionWhitelist = []
dimensionBlacklist = []
runtimeScanIntervalTicks = 100
runtimeChunksPerScan = 8
relocateEncasedSpawners = true
exposureBlocks = ["minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:water"]
minimumExposedSides = 2
relocationSearchRadius = 8

[combat]
baseHealth = 50.0
healthMode = "FIXED"
healthMultiplier = 1.0
maxScaledHealth = 200.0
armor = 0.0
knockbackResistance = 1.0
explosionDamage = true
projectileDamage = true
magicDamage = true
fireDamage = true
environmentalDamage = false
damageMultiplier = 1.0

[respawning]
enabled = true
delayMinutes = 30.0
clock = "SERVER_TIME"
scaleDelayWithMaxHealth = false

[spawning]
spawnCountMultiplier = 1.0
maxActiveMode = "INHERIT"
maxActiveMultiplier = 1.0
maxActiveOverride = 6
delayMultiplier = 1.0
playerRangeMode = "INHERIT"
playerRangeMultiplier = 1.0
fixedPlayerRange = 16
spawnRangeMultiplier = 1.0
requireLineOfSight = false
allowBossEntities = false
activateOnPeaceful = false
spawnWarningTicks = 40

[rewards]
xpEnabled = true
xpAmount = 15
lootEnabled = false
lootTable = "minecraft:empty"
despawnSummonsOnDeath = false
```

### Slower, durable dungeon objective

This creates a longer fight without greatly increasing the number of simultaneous enemies:

```toml
[combat]
baseHealth = 60.0
healthMode = "DIFFICULTY"
healthMultiplier = 1.0
maxScaledHealth = 100.0
armor = 4.0
damageMultiplier = 1.0

[spawning]
spawnCountMultiplier = 1.0
maxActiveMode = "OVERRIDE"
maxActiveOverride = 6
delayMultiplier = 1.5
playerRangeMode = "FIXED"
fixedPlayerRange = 20
requireLineOfSight = false

[rewards]
xpEnabled = true
xpAmount = 35
```

### Fast wave encounter

This increases the wave size and frequency while enforcing a hard simultaneous cap:

```toml
[combat]
baseHealth = 48.0
healthMode = "SPAWN_COUNT"
healthMultiplier = 1.0
maxScaledHealth = 160.0

[spawning]
spawnCountMultiplier = 2.0
maxActiveMode = "OVERRIDE"
maxActiveOverride = 12
delayMultiplier = 0.35
playerRangeMode = "FIXED"
fixedPlayerRange = 24
spawnRangeMultiplier = 1.0
```

## Troubleshooting

### A spawner does not convert

Check the following:

1. `enabled` must be `true` for automatic conversion.
2. Existing chunks require `convertExistingChunks=true`; runtime-created spawners may require `convertRuntimePlacedSpawners=true`.
3. A stored failed `conversionChance` result does not reroll after a restart or config change.
4. The dimension must pass the active dimension filter.
5. Every current and weighted entity entry must be registered and pass the active entity filter.
6. Boss-tagged entities require `allowBossEntities=true`.
7. The hard unsupported entity tag cannot be overridden through config.
8. An observed player placement in `KEEP` mode carries a persistent keep marker.
9. With relocation enabled, an encased spawner requires a valid exposed destination within the loaded search radius. If none exists, the original block is intentionally preserved.

Use `/foughtnotfarmed convert` while looking at a spawner for a permission-gated explicit attempt. Its feedback reports why an unsafe or invalid spawner was rejected.

### A Living Spawner stops spawning

- Confirm an alive, non-spectator player is within the effective range.
- If `requireLineOfSight=true`, confirm that player can see the cage.
- On Peaceful, `activateOnPeaceful=false` pauses the timer. Enabling it still does not bypass normal mob spawn rules.
- An effective active cap of zero prevents spawning.
- Loaded, living entities owned by that Living Spawner count against its cap.
- Changing filters or boss policy also affects existing Living Spawners when they next evaluate a spawn cycle.

The `/foughtnotfarmed debug` command reports the targeted Living Spawner's preserved pool, delay, effective ranges, and active owned-summon count.

### A destroyed Living Spawner does not return

- Confirm `[respawning].enabled=true` when the cage dies. A death that occurs while disabled is not scheduled retroactively.
- `SERVER_TIME` does not count time while the server is stopped; `SYSTEM_TIME` does.
- The death chunk must be loaded after the deadline. The queue intentionally does not force-load it.
- A living cage at the same anchor fulfills and removes the record instead of producing a duplicate.
- The clock and effective delay are captured at death. Later config changes affect newly scheduled deaths, not existing records.
- With `scaleDelayWithMaxHealth=true`, maximum health above 50 extends the configured delay.

### A supporting value appears to do nothing

Mode-specific values are intentionally ignored unless their corresponding mode selects them:

- `maxActiveMultiplier` requires `maxActiveMode="MULTIPLY"`.
- `maxActiveOverride` requires `maxActiveMode="OVERRIDE"`.
- `playerRangeMultiplier` requires `playerRangeMode="MULTIPLY"`.
- `fixedPlayerRange` requires `playerRangeMode="FIXED"`.

After editing, restart the relevant game or server. Check `logs/latest.log` if NeoForge rejects an out-of-range value or malformed TOML syntax.
