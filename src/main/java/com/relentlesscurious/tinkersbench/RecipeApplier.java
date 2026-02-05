package com.relentlesscurious.tinkersbench;

import com.hypixel.hytale.logger.HytaleLogger;
import com.relentlesscurious.tinkersbench.config.TinkersBenchConfig;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RecipeApplier {
  private final HytaleLogger logger;
  private final TinkersBenchConfig config;

  public RecipeApplier(HytaleLogger logger, TinkersBenchConfig config) {
    this.logger = logger;
    this.config = config;
  }

  public void applyHelper() {
    if (config == null || config.bikes == null) {
      return;
    }

    for (Map.Entry<String, TinkersBenchConfig.BikeConfig> entry : config.bikes.entrySet()) {
      String bikeName = entry.getKey();
      TinkersBenchConfig.BikeConfig bikeConfig = entry.getValue();

      if (bikeConfig.recipe == null || bikeConfig.recipe.ingredients == null
          || bikeConfig.recipe.ingredients.isEmpty()) {
        continue;
      }

      // Map bike name to key item id. E.g. "motorcycle" -> "motorcycle_key"
      String keyItemId;
      if (bikeName.contains("key")) {
        keyItemId = bikeName;
      } else {
        keyItemId = bikeName + "_key";
      }

      // Try to find the item in the asset map
      Item item = Item.getAssetMap().getAsset(keyItemId);

      // Fallback: Try capitalizing
      if (item == null) {
        String capName = bikeName.substring(0, 1).toUpperCase() + bikeName.substring(1) + "_Key";
        item = Item.getAssetMap().getAsset(capName);
      }

      if (item != null) {
        applyOverride(item, bikeConfig.recipe, bikeName);
        logger.atInfo().log("Applied recipe overrides for " + item.getId());
      } else {
        logger.atWarning().log("Could not find Item asset for " + keyItemId + " to apply recipe overrides.");
      }
    }
  }

  private void applyOverride(Item item, TinkersBenchConfig.RecipeConfig recipeConfig, String bikeName) {
    try {
      // Use reflection to get 'recipeToGenerate' from Item
      Field recipeField = Item.class.getDeclaredField("recipeToGenerate");
      recipeField.setAccessible(true);
      CraftingRecipe recipe = (CraftingRecipe) recipeField.get(item);

      if (recipe == null) {
        logger.atWarning().log("Item " + item.getId() + " has no existing recipe to override.");
        return;
      }

      // Create new Ingredient Array
      List<MaterialQuantity> newIngredients = new ArrayList<>();
      for (Map.Entry<String, Integer> ingredientEntry : recipeConfig.ingredients.entrySet()) {
        String key = ingredientEntry.getKey();
        int qty = ingredientEntry.getValue();

        String itemId = null;
        String resourceTypeId = null;

        if (key.startsWith("resource:")) {
          resourceTypeId = key.substring("resource:".length());
        } else {
          itemId = key;
        }

        // Constructor: MaterialQuantity(itemId, resourceTypeId, tag, quantity,
        // metadata)
        MaterialQuantity ingredient = new MaterialQuantity(itemId, resourceTypeId, null, qty, null);
        newIngredients.add(ingredient);
      }

      // Use reflection to set 'input' on CraftingRecipe
      Field inputField = CraftingRecipe.class.getDeclaredField("input");
      inputField.setAccessible(true);
      inputField.set(recipe, newIngredients.toArray(new MaterialQuantity[0]));

    } catch (Exception e) {
      logger.atSevere().log("Failed to apply recipe override for " + bikeName + ": " + e.getMessage());
      e.printStackTrace();
    }
  }
}
