package com.relentlesscurious.tinkersbench.config;

import java.util.Map;

public class TinkersBenchConfig {
  public Map<String, BikeConfig> bikes;

  public static class BikeConfig {
    public boolean craftable = true;
    public PowerConfig power;
    public RecipeConfig recipe;
  }

  public static class PowerConfig {
    public double baseSpeed;
    public double acceleration;
    public double forwardSprintSpeedMultiplier;
  }

  public static class RecipeConfig {
    public Map<String, Integer> ingredients;
  }
}
