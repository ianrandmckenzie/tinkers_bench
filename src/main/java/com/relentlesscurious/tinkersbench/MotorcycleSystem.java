package com.relentlesscurious.tinkersbench;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.world.PlaySoundEvent3D;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.MathUtil;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nonnull;

public class MotorcycleSystem extends DelayedEntitySystem<EntityStore> {
  private static final String SOUND_NAMESPACE = "relentlessCurious";
  private static final String SOUND_EVENT_PATH = "SFX/NPC/Vehicles/Motorcycle/";

  private final Map<Integer, String> lastSoundKey = new HashMap<>();
  private final Map<Integer, Long> lastSoundPlayMs = new HashMap<>();
  private final Map<String, Integer> soundIndexCache = new HashMap<>();
  private final Map<Integer, Vector3d> previousPositions = new HashMap<>();
  private SoundCategory resolvedSoundCategory;

  public MotorcycleSystem(HytaleLogger logger) {
    super(0.05f); // 20 TPS
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
    if (!entity.isValid())
      return;

    // Check if it's a motorcycle
    ModelComponent modelComp = store.getComponent(entity, ModelComponent.getComponentType());
    if (modelComp == null || modelComp.getModel() == null)
      return;

    String modelAssetId = modelComp.getModel().getModelAssetId();
    if (modelAssetId == null || !modelAssetId.toLowerCase().contains("motorcycle"))
      return;

    TransformComponent transform = store.getComponent(entity, TransformComponent.getComponentType());
    if (transform == null || transform.getPosition() == null)
      return;

    NetworkId netIdComp = store.getComponent(entity, NetworkId.getComponentType());
    if (netIdComp == null)
      return;
    int networkId = netIdComp.getId();

    // Check movement states (if ridden) or velocity (if autonomous/idle)
    MovementStatesComponent moveComp = store.getComponent(entity, MovementStatesComponent.getComponentType());
    boolean isRunning = false;
    boolean isWalking = false;
    boolean isSprinting = false;

    if (moveComp != null) {
      MovementStates states = moveComp.getMovementStates();
      isRunning = states.running;
      isWalking = states.walking;
      isSprinting = states.sprinting;
    } else {
      // Fallback: Manual velocity calculation
      Vector3d currentPos = transform.getPosition();
      Vector3d previousPos = previousPositions.get(networkId);

      double speed = 0.0;
      if (previousPos != null) {
        double dx = currentPos.x - previousPos.x;
        double dy = currentPos.y - previousPos.y;
        double dz = currentPos.z - previousPos.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        speed = dist / delta;
      }

      previousPositions.put(networkId, new Vector3d(currentPos.x, currentPos.y, currentPos.z));

      if (speed > 5.0f) {
        isRunning = true;
      } else if (speed > 0.1f) {
        isWalking = true;
      }
    }

    // Sound Logic
    String baseId = "SFX_Motorcycle_Idle";
    if (isSprinting) {
      baseId = "SFX_Motorcycle_Drive_Fast";
    } else if (isRunning || isWalking) {
      baseId = "SFX_Motorcycle_Drive";
    }

    String lastKey = lastSoundKey.get(networkId);
    Long lastPlayedAt = lastSoundPlayMs.get(networkId);
    long now = System.currentTimeMillis();

    int newSoundIndex = getSoundIndex(baseId);

    if (newSoundIndex < 0) {
      return;
    }

    boolean keyChanged = lastKey == null || !lastKey.equals(baseId);
    long intervalMs = getSoundIntervalMs(baseId);
    boolean intervalElapsed = lastPlayedAt == null || (now - lastPlayedAt) >= intervalMs;

    if (keyChanged || intervalElapsed) {
      lastSoundKey.put(networkId, baseId);
      lastSoundPlayMs.put(networkId, now);

      sendSoundPacket(store, transform, newSoundIndex, 1.0f);
    }
  }

  private int getSoundIndex(String simpleId) {
    return soundIndexCache.computeIfAbsent(simpleId, k -> {
      String namespacedPath = SOUND_NAMESPACE + ":" + SOUND_EVENT_PATH + k;
      String namespacedLower = SOUND_NAMESPACE.toLowerCase(Locale.ROOT) + ":" + SOUND_EVENT_PATH + k;
      String unnamespacedPath = SOUND_EVENT_PATH + k;

      int index = SoundEvent.getAssetMap().getIndex(unnamespacedPath);
      if (index >= 0)
        return index;

      index = SoundEvent.getAssetMap().getIndex(namespacedPath);
      if (index >= 0)
        return index;

      index = SoundEvent.getAssetMap().getIndex(namespacedLower);
      if (index >= 0)
        return index;

      index = SoundEvent.getAssetMap().getIndex(k);
      return Math.max(index, -1);
    });
  }

  private SoundCategory getSoundCategory() {
    if (resolvedSoundCategory != null) {
      return resolvedSoundCategory;
    }

    SoundCategory[] categories = SoundCategory.values();
    for (SoundCategory category : categories) {
      String name = category.name().toLowerCase(Locale.ROOT);
      if (name.equals("sfx") || name.contains("sfx")) {
        resolvedSoundCategory = category;
        return category;
      }
    }

    resolvedSoundCategory = categories.length > 0 ? categories[0] : null;
    return resolvedSoundCategory;
  }

  private long getSoundIntervalMs(String soundId) {
    if (soundId == null) {
      return 2000L;
    }

    if (soundId.contains("Drive_Fast")) {
      return 50L;
    }
    if (soundId.contains("Drive")) {
      return 100L;
    }
    return 100L;
  }

  private void sendSoundPacket(Store<EntityStore> store, TransformComponent transform, int soundIndex,
      float volume) {
    Vector3d position = transform.getPosition();

    SoundCategory category = getSoundCategory();
    Position soundPos = new Position(position.x, position.y, position.z);
    PlaySoundEvent3D packet = new PlaySoundEvent3D(soundIndex, category, soundPos, volume, 1.0f);
    long chunkIndex = ChunkUtil.indexChunkFromBlock(MathUtil.floor(position.x), MathUtil.floor(position.z));
    store.getExternalData().getWorld().getNotificationHandler().sendPacketIfChunkLoaded(packet, chunkIndex);
  }
}
