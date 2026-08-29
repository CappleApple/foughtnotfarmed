# Architecture

## Conversion scheduling

`SpawnerConversionManager` separates detection from mutation:

1. Chunk-load events enqueue a chunk key and return immediately. They never wait for `ChunkStatus.FULL` or perform level interaction on a generation worker.
2. The next server-level tick resolves the chunk with `getChunkNow` and copies `getBlockEntitiesPos()`.
3. Only positions whose current block is `minecraft:spawner` are considered.
4. Entity/block placement events enqueue their exact position. A bounded round-robin pass covers `/setblock`, structure blocks, scripts, and mods that do not emit a placement event.
5. Chunk unload removes the key from the loaded set, and level unload drops all scheduler state.

There is no per-tick full-chunk block scan and no world-wide index.

## Transactional conversion

Conversion validates the live block and block entity, serializes the vanilla `BaseSpawner`, decodes the state, applies dimension/entity/tag policy, and checks for an existing Living Spawner with the same original conversion position.

The entity is added before the block is removed. If entity addition is rejected, the block remains. The block entity identity and block state are checked again immediately before replacement; if either changed, the new entity is discarded. If block removal fails, the new entity is discarded. A later pass recognizes the add-first recovery case and removes the leftover block instead of creating a second entity.

This ordering minimizes lost spawners and provides duplicate recovery without persistent global location data. Minecraft saves block and entity storage separately, so no mod can provide an actual cross-storage database transaction; the recovery check is the deliberate crash-consistency compromise.

## Preserved spawner state

`SpawnerState` decodes the same `SpawnData` and `SpawnPotentials` codecs used by Minecraft 1.21.1. It retains the complete original custom tag, including unknown mod fields, and updates only known vanilla fields on save. Entity NBT is passed to `EntityType.loadEntityRecursive` rather than being reduced to an entity ID.

The selected weighted entry is synchronized to clients for rendering. The full weighted pool remains server-only.

## Spawning

The entity uses a countdown timer and checks player activation once per second. At the warning threshold it follows vanilla `BaseSpawner` behavior for candidate coordinates, collision, world border, custom light rules, `SpawnPlacements`, and entity loading. A candidate must also pass NeoForge's position hook before the warning begins. The exact unspawned entity is retained through the full configured warning, then its position and the current summon cap are checked again before finalization, equipment, insertion, level events, and mob spawn animation. Remaining attempts use the same candidate search as before.

Block and sky light overrides are evaluated independently. Preserved `CustomSpawnRules` test each non-ignored channel directly. Vanilla placement checks run inside a server-thread `ThreadLocal` scope; narrow mixins replace only the light portions of Minecraft's monster, animal, bat, glow-squid, and pillager predicates while the scope is active. The same scope covers NeoForge's post-load spawner position check, including the monster walk-target light rule. Outside a Living Spawner candidate check, the mixins return vanilla behavior unchanged.

NeoForge's `checkSpawnPositionSpawner` and `finalizeMobSpawnSpawner` hooks require an owned `BaseSpawner`; a small delegate returns the Living Spawner as its owner. This retains spawn-cancellation and modification opportunities for other mods without ticking vanilla's private `BaseSpawner` implementation in parallel.

Failed candidate positions use a separate 20-tick retry delay instead of performing expensive work every tick or re-entering the audible warning. The attempted-spawn flag keeps blue flames active independently of warning shake and sound. Prepared entities are transient and cleared on deactivation, death, or NBT reload. Invalid dynamic state resets the normal delay and emits a rate-limited warning. Successful-spawn effects require the root entity to have actually entered the level, since `tryAddFreshEntityWithPassengers` can report success even when an insertion event was cancelled.

## Ownership and cap

Every successfully inserted root entity receives the Living Spawner UUID in NeoForge persistent entity data. The owner stores a bounded UUID set, prunes missing/dead/mismatched entries before a spawn cycle, and performs one nearby owner-marker rebuild after load. Ownership does not force mob persistence.

## Death and respawn persistence

When respawning is enabled, death stores a per-dimension `SavedData` record containing the death position, original conversion position, complete current spawner NBT, chosen clock mode, and due time. The clock mode is captured when the cage dies, so changing the config later does not reinterpret an existing timer.

The optional dormant phase places a real vanilla spawner at the death anchor and assigns the block plus pending record a shared UUID. The block retains the original spawner state both as its live data and in NeoForge persistent data. A narrow `BaseSpawner` mixin pauses client and server ticks only while that private marker remains at its recorded position and dimension, disabling activation without contaminating data seen by compatible mining or transport mods. Conversion scans skip only the correctly anchored marker, so copied block-entity data cannot leave a transported spawner dormant.

A lowest-priority player break listener restores the original state before normal harvesting and removes the UUID-matched pending record. This lets compatible mining mods capture ordinary spawner data and prevents a mined block from later reactivating at its former position. Direct replacement or removal is detected from the missing/mismatched UUID at the deadline and also cancels reactivation.

`SERVER_TIME` stores a due game tick. World game time does not advance while the server is stopped, so offline time is excluded. `SYSTEM_TIME` stores an epoch-millisecond deadline and therefore includes offline wall-clock time. Optional health adjustment multiplies the configured delay by `maximum health / 50`.

Due records are processed once per second. They do not force-load chunks: a due record remains queued until its death chunk is loaded. Respawn recreates a full-health Living Spawner at the death anchor from the stored state. A dormant record first verifies the exact marked block, adds the entity, and removes that block; a missing or replaced block cancels the record. If another living cage already occupies the anchor, the record is treated as fulfilled; a rejected entity insertion is retained and retried later.

## Entity and sided design

`LivingSpawnerEntity` extends `Mob` and implements `Enemy`, but registers no goals and cannot change dimensions, travel, receive knockback, respond to fluids, or be pushed by pistons. It is persistent and does not despawn on Peaceful.

Before entity creation, an optional bounded exposure search keeps an already accessible source position or selects the nearest valid destination in loaded chunks. The active anchor and original conversion position are persisted separately: spawning, rendering, and combat use the anchor, while interrupted-conversion recovery identifies the original block position.

All conversion, health, spawning, ownership, death, and rewards are server-authoritative. The client entry point is `Dist.CLIENT` only and registers a renderer that uses the vanilla spawner block state plus `SpawnerRenderer.renderEntityInSpawner`. Separate synced flags drive active particles, attempted-spawn blue flames, and warning shake/preview acceleration, while a standard entity event triggers the spawn pulse. No client class is referenced by the common mod entry point, and no custom gameplay packet is required.
