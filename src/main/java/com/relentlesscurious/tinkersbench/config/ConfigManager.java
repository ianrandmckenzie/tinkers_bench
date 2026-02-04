package com.relentlesscurious.tinkersbench.config;

import com.google.gson.Gson;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.io.InputStream;
import java.nio.file.StandardCopyOption;

public class ConfigManager {
  private final JavaPlugin plugin;
  private final Gson gson;
  private TinkersBenchConfig config;

  public ConfigManager(JavaPlugin plugin) {
    this.plugin = plugin;
    this.gson = new Gson();
  }

  public void loadConfig() {
    File modsDir = new File("mods");
    if (!modsDir.exists()) {
      modsDir.mkdirs();
    }
    File configFile = new File(modsDir, "tinkers_bench.json");

    // If config doesn't exist, try to copy from resources
    if (!configFile.exists()) {
      try (InputStream is = plugin.getClass().getClassLoader().getResourceAsStream("config.json")) {
        if (is != null) {
          Files.copy(is, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
          plugin.getLogger().atInfo().log("Created default tinkers_bench.json");
        }
      } catch (IOException e) {
        plugin.getLogger().atSevere().log("Failed to create default tinkers_bench.json: " + e.getMessage());
      }
    }

    if (configFile.exists()) {
      try (FileReader reader = new FileReader(configFile)) {
        config = gson.fromJson(reader, TinkersBenchConfig.class);
        plugin.getLogger().atInfo().log("Loaded TinkersBench config.");
      } catch (IOException e) {
        plugin.getLogger().atSevere().log("Failed to load tinkers_bench.json: " + e.getMessage());
      }
    }
  }

  public TinkersBenchConfig getConfig() {
    return config;
  }
}
