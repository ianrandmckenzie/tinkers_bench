# How to Add a New Bike to Tinkers Bench

This guide outlines the steps required to add a new motorcycle variant (e.g., "Steambike") to the mod. The process involves adding assets, configuring server behavior, and updating the Java code.

## 1. Asset Preparation (Client-Side)

Add your visual assets to the `src/main/resources/Common` directory.

### Entity Assets
Location: `src/main/resources/Common/NPC/Vehicles/[BikeName]/`
*   **Model:** `[BikeName].blockymodel`
*   **Texture:** `[BikeName].png`
*   **Animations:** Place `.blockyanim` files in an `Animations/Default/` subdirectory.
    *   *Note: You can reuse existing animations (like Motorcycle's) in the Model Config step if you don't have custom ones.*

### Item Assets (The Key/Remote)
Location: `src/main/resources/Common/Items/`
*   **Model:** `[BikeName]_Remote.blockymodel`
*   **Texture:** `[BikeName]_Remote.png`

Location: `src/main/resources/Common/Icons/ItemsGenerated/`
*   **Icon:** `[BikeName]_Keys.png` (Used in inventory)

---

## 2. Server Configuration

Create the following JSON configuration files in `src/main/resources/Server`.

### A. Movement Configuration
**File:** `Entity/MovementConfig/[BikeName].json`
*   Defines physics (speed, jump heights, friction).
*   *Tip: Copy `Motorcycle.json` and adjust `BaseSpeed`, `Acceleration`, etc.*

### B. Model Configuration
**File:** `Models/Vehicles/[BikeName].json`
*   Links the logical model definition to your assets.
*   **Crucial:** Points to the `.blockymodel` and `.blockyanim` files in `Common`.

### C. Drop Configuration
**File:** `Drops/Vehicles/Drop_[BikeName].json`
*   Defines what drops when the bike is destroyed.
*   Should drop the specific Key item for this bike.

### D. NPC Role Configuration
**File:** `NPC/Roles/Vehicles/[BikeName].json`
*   Defines the entity behavior.
*   **Essentials:**
    *   `"Appearance": "[BikeName]"`
    *   `"DropList": "Drop_[BikeName]"`
    *   `"MountMovementConfig": "[BikeName]"`
    *   **InteractionInstruction:** Ensure the "Mount" action is defined here so players can verify ride it.

### E. Item Configuration
**File:** `Item/Items/[BikeName]_Key.json`
*   Defines the key item.
*   **Interactions:** Must include `"Type": "SpawnNPC"` with `"EntityId": "[BikeName]"`.

### F. Translations
**File:** `Languages/en-US/server.lang`
Add the following keys:
```properties
[bikename].name = My Bike Name
[bikename].key.name = My Bike Key
[bikename].key.description = Description of the key.
[bikename].interactionHints.mount = Press [{key}] to Ride
# Key Interaction Translations
[bikename]_Key_Interactions_Primary_Interactions_0 = Spawn Bike
[bikename]_Key_Interactions_Primary_0 = Spawn Bike
relentlesscurious_[bikename]_Key_Interactions_Primary_0 = Spawn Bike
relentlesscurious_[bikename]_Key_Interactions_Primary_Interactions_0 = Spawn Bike
```

---

## 3. Java Code Updates

You must update the Java systems to recognize the new bike name.

### A. MotorcycleSystem.java
Updates the sound engine and physics logic.
*   **Method:** `tick()`
*   **Action:** Update the check for `modelAssetId`.
    ```java
    // Check if it includes your new bike name
    if (modelAssetId == null || (!modelAssetId.contains("motorcycle") && !modelAssetId.contains("steambike")))
      return;
    ```

### B. MotorcycleKeySystem.java
Updates the tracking logic for the key system.
*   **Method:** `tick()`
*   **Action:** Update the check for `modelAssetId` similarly to `MotorcycleSystem`.
    ```java
    if (modelAssetId == null || (!modelAssetId.contains("motorcycle") && !modelAssetId.contains("steambike"))) {
      return;
    }
    ```

### C. MotorcycleKeyTracker.java
Ensures the tracker recognizes the new item as a valid vehicle key.
*   **Method:** `isMotorcycleKey(ItemStack itemStack)`
*   **Action:** Add your key's name to the check.
    ```java
    return normalizedPath.contains("motorcycle_key") || normalizedPath.contains("steambike_key");
    ```

---

## Summary Checklist
- [ ] Assets in `Common/`
- [ ] Movement Config
- [ ] Model Config
- [ ] Drop Config
- [ ] NPC Role Config
- [ ] Item Config
- [ ] Translations in `server.lang`
- [ ] Java: Update `MotorcycleSystem`
- [ ] Java: Update `MotorcycleKeySystem`
- [ ] Java: Update `MotorcycleKeyTracker`
