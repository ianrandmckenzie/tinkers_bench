package com.relentlesscurious.tinkersbench;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.vector.Vector3d;
import javax.annotation.Nonnull;

public class MotorcycleKeySystem extends DelayedEntitySystem<EntityStore> {
  private final MotorcycleKeyTracker tracker;

  public MotorcycleKeySystem(MotorcycleKeyTracker tracker) {
    super(0.1f);
    this.tracker = tracker;
  }

  @Override
  public Query<EntityStore> getQuery() {
    return Query.and(
        ModelComponent.getComponentType(),
        TransformComponent.getComponentType(),
        NetworkId.getComponentType());
  }

  @Override
  @SuppressWarnings("null")
  public void tick(float delta, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
      @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> buffer) {
    Ref<EntityStore> entity = chunk.getReferenceTo(index);
    if (!entity.isValid()) {
      return;
    }

    ModelComponent modelComp = store.getComponent(entity, ModelComponent.getComponentType());
    if (modelComp == null || modelComp.getModel() == null) {
      return;
    }

    String modelAssetId = modelComp.getModel().getModelAssetId();
    if (modelAssetId == null
        || (!modelAssetId.toLowerCase().contains("motorcycle") && !modelAssetId.toLowerCase().contains("steambike")
            && !modelAssetId.toLowerCase().contains("voidchariot"))) {
      return;
    }

    TransformComponent transform = store.getComponent(entity, TransformComponent.getComponentType());
    if (transform == null) {
      return;
    }

    NetworkId netIdComp = store.getComponent(entity, NetworkId.getComponentType());
    if (netIdComp == null) {
      return;
    }

    int netId = netIdComp.getId();
    if (!tracker.isTracked(netId)) {
      System.out
          .println("DEBUG: MotorcycleKeySystem found untracked motorcycle ID=" + netId + " Model=" + modelAssetId);
    }

    Vector3d position = transform.getPosition();
    tracker.onMotorcycleSeen(netId, position);
  }
}
