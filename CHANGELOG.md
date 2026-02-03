# Changelog

All notable changes to this project will be documented in this file.

## [1.0.0] - 2023-10-27

### Added
- Initial release of **Tinkers' Bench**.
- Motorcycle entity with custom model and animations.
- `MotorcycleKeyTracker` for summoning/unsummoning personal vehicles.
- `MotorcycleSystem` ECS system handling:
  - Velocity-based animation switching (Idle, Walk, Run, Sprint).
  - Server-side sound management with re-trigger logic.
- Sound assets configuration:
  - `MaxInstance` set to 20 for multiplayer compatibility.
  - `Looping` set to false (handled by server logic).

### Fixed
- Addressed sound drop-outs by tuning re-trigger intervals for `Drive` (100ms) and `Drive_Fast` (50ms).
- Resolved multiplayer audio conflicts by increasing `MaxInstance` limits.
