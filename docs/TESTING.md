# Testing and release acceptance

## Automated validation

Run with Java 21:

```powershell
.\gradlew.bat test build --stacktrace
```

The unit suite covers vanilla defaults, malformed/empty state, weighted pools, custom modded entity NBT, round-trip persistence, spawn-delay multiplication, health modes, respawn-delay clock units and health scaling, dormant-state preservation, active-cap modes, range tuning, hard bounds, and explicit whitelist/blacklist semantics.

## Dedicated-server smoke test

1. Run `.\gradlew.bat runServer`.
2. Accept `eula=true` in the generated development server when needed.
3. Confirm NeoForge discovers `Fought Not Farmed 1.3` without loading client renderer classes.
4. Confirm a world is created and the log reaches `Done`.
5. Stop through the server console or RCON.
6. Confirm the world saves and shutdown completes cleanly.

## Gameplay acceptance checklist

Use a disposable test world and permission level 2.

The Gradle client run uses `run-client/` while the dedicated server uses `run/`, preventing their logs and runtime files from colliding during connected-client checks.

### Conversion and duplication

- Place/configure zombie and skeleton spawners and confirm each becomes exactly one Living Spawner.
- Save, exit, reload the chunk, and confirm no block or entity duplicate appears.
- Add the mod to a world containing old spawners and confirm loaded old chunks convert.
- Put an entity ID in the blacklist and confirm all weighted spawners containing it remain blocks.
- Test player placement under `CONVERT`, `KEEP`, and `DISALLOW`.
- Run `/setblock ~ ~ ~ minecraft:spawner` and confirm the runtime scan eventually converts it.

### Spawning and ownership

- Confirm the configured entity and any custom NBT spawn.
- Confirm spawn count, initial delay, weighted potentials, player range, and spawn range resemble the source block.
- Leave the activation radius and confirm the countdown pauses; return and confirm it resumes without multiplying for multiple players.
- Reach the active cap, kill an owned mob, and confirm a later cycle can refill the freed slot.
- Save/reload while summons are alive and use `/foughtnotfarmed debug` to inspect ownership recovery.

### Combat and death

- Damage the cage with melee, arrows, magic, fire, and an explosion.
- Confirm suffocation, falling, drowning, cactus, and cramming do not silently destroy it under defaults.
- Confirm the cage cannot be pushed or knocked away from its source block.
- Kill it and confirm spawning stops immediately, break/soul effects play, XP follows config, and existing summons remain alive by default.

### Respawning

- With `enabled=true`, set a short fractional `delayMinutes`, kill a cage, and confirm exactly one full-health replacement appears at the same position.
- With `clock="SERVER_TIME"`, stop the server during the timer and confirm offline time does not reduce the remaining delay.
- With `clock="SYSTEM_TIME"`, stop beyond the deadline and confirm the cage returns when its dimension and death chunk load.
- Enable `scaleDelayWithMaxHealth` and confirm 100 maximum health takes twice the configured delay while 25 health takes half.
- Disable respawning, kill a cage, and confirm no record is scheduled. Unload a death chunk before a due timer and confirm it is not force-loaded.
- Enable `leaveDormantSpawnerBlock`, kill a cage, and confirm a vanilla spawner with the correct mob preview and original `RequiredPlayerRange` remains but does not activate, spin, emit flame/smoke, or spawn mobs.
- Leave that marked block in place and confirm the deadline atomically replaces it with one full-health Living Spawner.
- Mine a dormant block with a compatible spawner-mining mod and confirm the item retains its original mob data and no Living Spawner later appears at the old position.
- Replace a dormant block through a command or another mod and confirm its pending reactivation is cancelled.

### Client and compatibility

- Confirm the spawner block model aligns to the replaced block and the one-block hitbox is reliable.
- Encase a spawner, convert it, and confirm it relocates to the nearest loaded configured block position with at least the configured number of exposed faces.
- Confirm an already exposed spawner remains at its original position and that disabling relocation leaves an encased conversion in place.
- Confirm active cages emit vanilla smoke and flame particles.
- Confirm the cage shakes and plays one warning sound when its delay enters `spawnWarningTicks`, then briefly grows and shrinks after a successful spawn cycle.
- Confirm the selected preview rotates, scales, speeds up before spawning, and changes with weighted selection.
- Join a dedicated server with two clients and confirm one authoritative spawn cycle occurs.
- Test a normal spawner from at least one structure datapack/mod and one registered modded entity with custom NBT.
