# Tinkers' Bench

**Tinkers' Bench** is a Hytale server-side mod (plugin) that introduces functional, rideable motorcycles to the game. It is built using the Hytale Entity Component System (ECS) and features a robust sound and animation system designed for multiplayer compatibility.

## Features

- **Rideable Motorcycles**: fast-paced traversal with custom physics/movement states.
- **Key System**:
  - Use the **Motorcycle Key** item to summon your personal motorcycle.
  - Interactions with the key handle summoning and unsummoning logic.
- **Dynamic Audio**:
  - Realistic engine sounds that adapt to the bike's state (Idle, Drive, Fast Drive).
  - Optimized for multiplayer with `MaxInstance` tuning to allow overlapping engine sounds without cutting out.
- **Animations**:
  - Full animation state machine support (Idle, Walk, Run, Sprint) mapped to vehicle speed.
- **Configuration**:
  - Adjustable bike power (speed, acceleration) via `config.json`.
  - Configurable crafting recipes.

## Configuration

A `tinkers_bench.json` file will be generated in the server's `mods` directory upon first run. You can edit this file to adjust bike stats:

```json
{
  "bikes": {
    "motorcycle": {
      "power": {
        "baseSpeed": 15.0,
        "acceleration": 0.5,
        "forwardSprintSpeedMultiplier": 2.5
      }
    }
  }
}


## Installation

1. Build the project using Maven:
   ```bash
   mvn clean package
   ```
2. Place the generated `.jar` file into your Hytale server's `mods` or `plugins` directory.
3. Ensure the server has the required assets (models, animations, sounds) in the correct namespace (`relentlessCurious` / `TinkersBench`).

## Usage

1. Obtain a **Motorcycle Key** (Item ID: `motorcycle_key`).
2. **Right-Click** on a block to summon your motorcycle.
3. **Right-Click** the motorcycle to mount it.
4. Use standard movement keys to drive.
5. **Right-Click** with the key again to unsummon (store) the bike.

## Technical Details

- **Group**: `relentlessCurious`
- **Name**: `TinkersBench`
- **Dependencies**: Hytale Server API

The mod uses a `DelayedEntitySystem` (20 TPS) to monitor motorcycle entities, updating their sound loop and animation state based on velocity and rider input.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
