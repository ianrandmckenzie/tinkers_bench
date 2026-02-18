# Scriptorium Golem Feature Breakdown

## Overview
The Scriptorium Golem is a stationary guardian/observer summoned by the **Scribes' Hourglass** block. When a **Golem Book** is placed adjacent to the hourglass, the golem records nearby events into the book for player review.

---

## Phase 1: Core Assets & NPC Definitions
### Task 1.1: Scribes' Hourglass Block
- [x] Block model moved to `Common/Blocks/hourglass/`.
- [x] Block definition JSON created in `Server/Block/Blocks/hourglass.json`.
- [x] Item definition created for the block in `Server/Item/Items/hourglass.json`.

### Task 1.2: Golem Book Block
- [x] Block model moved to `Common/Blocks/golem_book/`.
- [x] Block definition JSON created in `Server/Block/Blocks/golem_book.json`.
- [x] Item definition created for the block in `Server/Item/Items/golem_book.json`.

### Task 1.3: Scriptorium Golem NPC
- [x] NPC Role JSON created in `Server/NPC/Roles/scriptorium_golem.json`.
- [x] NPC Appearance/Model JSON created in `Server/Models/scriptorium_golem.json`.
- [x] Assets moved and normalized in `Common/NPC/scriptorium_golem/`.

---

## Phase 2: Lifecycle Management
### Task 2.1: Summoning System
- **Description:** Detect Hourglass placement to spawn the Golem.
- **Status:** 🔄 Implemented — pending server acceptance test.
- **Implementation:**
    - `HourglassPlaceSystem` (`EntityEventSystem<EntityStore, PlaceBlockEvent>`)
    - `ScriptoriumGolemTracker` (block pos → golem `Ref` map)
    - Detection: `event.getItemInHand().getItemId()` (contains `"hourglass"`, case-insensitive)
    - Spawn: `NPCPlugin.get().spawnNPC(store, "Scriptorium_Golem", null, pos, Vector3f.ZERO)`
    - All block placements are logged for diagnostic confirmation of block ID format.
- **Criteria:**
    - A system or event listener triggers on `Scribes_Hourglass` placement. ✅ (`HourglassPlaceSystem` registered)
    - Golem entity is spawned at the block location. 🔄 (needs server confirmation)

### Task 2.2: Binding & Cleanup
- **Description:** Link the Golem to the block.
- **Status:** 🔄 Implemented — pending server acceptance test.
- **Implementation:**
    - `HourglassBreakSystem` (`EntityEventSystem<EntityStore, BreakBlockEvent>`)
    - Detection: `tracker.isBound(pos)` — primary guard, avoids needing block ID at break time
    - Cleanup: `tracker.unbind(pos)` + `commandBuffer.removeEntity(golemRef, RemoveReason.REMOVE)` deferred via `world.execute()`
    - All block breaks are logged for diagnostic confirmation of position format.
- **Criteria:**
    - If the Hourglass block is removed, the associated Golem is killed immediately. 🔄 (needs server confirmation)
    - Ensure only one Golem per Hourglass. ✅ (`tracker.isBound()` guard in `HourglassPlaceSystem`)

---

## Phase 3: Monitoring & Logging (The ECS System)
### Task 3.1: Proximity Detection
- **Description:** Verify the "Golem Book" is adjacent to the "Scribes' Hourglass".
- **Status:** 🔄 Implemented — pending server acceptance test.
- **Implementation:**
    - `GolemBookPlaceSystem` (`EntityEventSystem<EntityStore, PlaceBlockEvent>`) — registers book positions in tracker; checks adjacency to any tracked hourglass on placement.
    - `GolemBookBreakSystem` (`EntityEventSystem<EntityStore, BreakBlockEvent>`) — deregisters book positions on removal.
    - `ScriptoriumGolemTracker` updated with `addBook` / `removeBook` / `isBookAdjacent(hourglassPos)` / `findAdjacentHourglass(bookPos)`, checking all 6 face-neighbors.
    - `HourglassPlaceSystem` updated to check for a pre-existing adjacent book on hourglass placement.
    - All events surface results as in-game chat messages (`Message.raw(...)` via `Player.sendMessage`) — no need to tail server logs.
- **Criteria:**
    - Logging only occurs if the Book block is within 1 block of the Hourglass. 🔄 (adjacency gate implemented; needs server confirmation)

### Task 3.2: Event Interception
- **Description:** Detect "Sights and Sounds".
- **Status:** 🔄 Implemented — pending server acceptance test.
- **Implementation:**
    - `GolemPresenceSystem` (`DelayedEntitySystem<EntityStore>`, 0.5 s tick, `PlayerRef` query) — polls each player's `TransformComponent` position every 0.5 s; logs `"Player '...' entered/departed monitoring radius"` on edge transitions for every active hourglass within 15 blocks.  In-range state tracked in a `ConcurrentHashSet` keyed by `refId@hgKey` so only enter/exit transitions are logged.
    - `GolemSightEventSystem` (`EntityEventSystem<EntityStore, UseBlockEvent.Pre>`, `PlayerRef` query) — fires on every block interaction by a player; if the player is within 15 blocks of an active hourglass, classifies the block type (chest/door/lever/furnace/generic) and appends an entry to the hourglass log.
    - `ScriptoriumGolemTracker` updated with `isActive(pos)`, `LogEntry` (immutable record with wall-clock `timestampMs` + `description`), `addLog(pos, text)`, `getLogs(pos)`, and `clearLogs` (called automatically on `unbind`).
    - All detections are also surfaced via `System.out` / logger for server-log confirmation.
- **Criteria:**
    - Detect NPCs/Players entering a 15-block radius. 🔄 (player enter/exit implemented; NPC detection deferred pending confirmed NPC component query pattern)
    - Detect nearby Interaction events (Chests opening, Doors moving). 🔄 (`UseBlockEvent.Pre` captured; block-type keywords logged raw until IDs are confirmed in server logs)
    - Detect Crop Maturity events in radius. ⏳ (no `CropGrowEvent` found in server jar — will revisit if a suitable hook is discovered or confirmed via server logs)

### Task 3.3: Writing to the Log
- **Description:** Format detected events into text entries.
- **Criteria:**
    - Entries include a timestamp (game time) and event description.

---

## Phase 4: UI & Interaction
### Task 4.1: Book Interface
- **Description:** Create the logbook UI.
- **Criteria:**
    - Interacting with the **Golem Book** opens a scrollable UI.
    - Displays the recorded logs from Task 3.3.

---

## Phase 5: Behavioral Refinement
### Task 5.1: Combat Logic
- **Description:** Enforce the "Passive unless attacked" rule.
- **Criteria:**
    - Golem ignores players until damaged.
    - Returns to neutral if the target leaves the radius or is defeated.
