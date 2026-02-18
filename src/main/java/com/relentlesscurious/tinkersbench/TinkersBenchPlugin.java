package com.relentlesscurious.tinkersbench;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.relentlesscurious.tinkersbench.config.ConfigManager;
import javax.annotation.Nonnull;


/**
 * Tinkers Bench Plugin for Hytale.
 * Handles specialized motorcycle entity logic.
 */
public class TinkersBenchPlugin extends JavaPlugin {
  private ConfigManager configManager;

  public TinkersBenchPlugin(@Nonnull JavaPluginInit init) {
    super(init);
  }

  @Override
  protected void setup() {
    getLogger().atInfo().log("Tinkers Bench setup() called.");

    this.configManager = new ConfigManager(this);
    this.configManager.loadConfig();

    // Apply recipe overrides using direct Asset modification
    new RecipeApplier(getLogger(), configManager.getConfig()).applyHelper();

    // Event listeners disabled in favor of JSON configuration for Hytale compliance
    // getEventRegistry().registerGlobal(EntityRemoveEvent.class,
    // this::handleEntityRemove);
    // getEventRegistry().registerGlobal(PlayerInteractEvent.class,
    // this::handleInteract);

    getEntityStoreRegistry().registerSystem(new MotorcycleSystem(getLogger(), configManager.getConfig()));

    // --- Scriptorium Golem: summoning & cleanup systems (Tasks 2.1 / 2.2) ---
    ScriptoriumGolemTracker golemTracker = new ScriptoriumGolemTracker();
    getEntityStoreRegistry().registerSystem(new HourglassPlaceSystem(getLogger(), golemTracker));
    getLogger().atInfo().log("Tinkers Bench: HourglassPlaceSystem registered.");
    getEntityStoreRegistry().registerSystem(new HourglassBreakSystem(getLogger(), golemTracker));
    getLogger().atInfo().log("Tinkers Bench: HourglassBreakSystem registered.");
    // Re-binds persisted Scriptorium Golem NPCs into the tracker after a server restart.
    getEntityStoreRegistry().registerSystem(new GolemRebindSystem(getLogger(), golemTracker));
    getLogger().atInfo().log("Tinkers Bench: GolemRebindSystem registered.");

    // --- Scriptorium Golem: Golem Book proximity tracking (Task 3.1) ---
    getEntityStoreRegistry().registerSystem(new GolemBookPlaceSystem(getLogger(), golemTracker));
    getLogger().atInfo().log("Tinkers Bench: GolemBookPlaceSystem registered.");
    getEntityStoreRegistry().registerSystem(new GolemBookBreakSystem(getLogger(), golemTracker));
    getLogger().atInfo().log("Tinkers Bench: GolemBookBreakSystem registered.");

    // --- Scriptorium Golem: Event interception (Task 3.2) ---
    getEntityStoreRegistry().registerSystem(new GolemPresenceSystem(getLogger(), golemTracker));
    getLogger().atInfo().log("Tinkers Bench: GolemPresenceSystem registered.");
    getEntityStoreRegistry().registerSystem(new GolemNPCPresenceSystem(getLogger(), golemTracker));
    getLogger().atInfo().log("Tinkers Bench: GolemNPCPresenceSystem registered.");
    getEntityStoreRegistry().registerSystem(new GolemSightEventSystem(getLogger(), golemTracker));
    getLogger().atInfo().log("Tinkers Bench: GolemSightEventSystem registered.");
  }
}
