# Changelog

## 1.2.1 - 2026-08-17

### Added

- Added the Fought Not Farmed logo as the in-game mod-list icon.

### Changed

- Aligned the packaged mod metadata with the repository's MIT License.

## 1.2 - 2026-08-16

### Added

- Persistent, configurable Living Spawner respawns at the position where the cage died.
- Configurable 30-minute delay with server-time or system-time tracking and optional maximum-health-based delay scaling.

### Changed

- Increased the default Living Spawner health from 36 to 50.

## 1.1 - 2026-08-16

### Added

- Configurable relocation of encased spawners to the nearest loaded air, water, or other configured block position with a configurable minimum number of exposed faces.
- Vanilla-style smoke and flame particles inside active Living Spawner cages.
- A configurable pre-spawn warning window with cage shake and a one-shot warning sound.
- A small grow-and-shrink cage pulse when a spawn cycle succeeds.

### Changed

- Persist the original conversion position separately from a relocated Living Spawner's active anchor so interrupted conversions remain recoverable.

## 1.0.1 - 2026-08-16

### Fixed

- Kept client-side Living Spawner entities at their server-tracked position instead of snapping their rendered cage and selectable hitbox to the world origin.
- Explicitly retained player targeting and attackability while the Living Spawner is alive.

## 1.0.0 - 2026-08-16

### Added

- Automatic, server-thread conversion of vanilla spawner block entities from new structures, existing chunks, runtime placement, and bounded round-robin loaded-chunk scans.
- Stationary, attackable Living Spawner entity with configurable combat, activation, spawn limits, rewards, persistence, and damage behavior.
- Generic preservation of vanilla and modded `SpawnData`, weighted `SpawnPotentials`, custom entity NBT, timing, counts, caps, and ranges.
- Persistent spawned-entity ownership tracking and active-summon enforcement.
- Vanilla spawner-block rendering, rotating current-mob preview, cage shake, and activation/damage/death effects.
- Entity/dimension allow and deny filters, datapack exclusion/boss tags, and safe player-placement policies.
- Permission-gated conversion, area, chunk, and debug commands.
- Java 21 unit tests, dedicated-server acceptance instructions, and architecture documentation.
