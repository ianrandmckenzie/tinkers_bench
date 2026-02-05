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
  }
}
