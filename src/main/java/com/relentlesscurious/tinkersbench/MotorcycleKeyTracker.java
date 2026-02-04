package com.relentlesscurious.tinkersbench;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MotorcycleKeyTracker {
  private static final long PENDING_TTL_MS = 10000;
  private static final double ASSIGN_RADIUS_SQ = 100.0;

  private final HytaleLogger logger;
  private final Map<UUID, PendingSpawn> pendingByPlayer = new ConcurrentHashMap<>();
  private final Map<UUID, Integer> activeMotorcycleByPlayer = new ConcurrentHashMap<>();
  private final Map<Integer, UUID> ownerByNetworkId = new ConcurrentHashMap<>();

  public MotorcycleKeyTracker(HytaleLogger logger) {
    this.logger = logger;
  }

  public boolean isMotorcycleKey(ItemStack itemStack) {
    if (itemStack == null) {
      return false;
    }

    String itemId = itemStack.getItemId();
    if (itemId == null || itemId.isEmpty()) {
      return false;
    }

    String normalized = itemId.toLowerCase(Locale.ROOT);
    if (normalized.contains("remote") || normalized.contains("controller")) {
      return true;
    }

    String normalizedPath = normalized.replace('\\', '/');
    return normalizedPath.contains("motorcycle_key") || normalizedPath.contains("steambike_key");
  }

  public boolean hasPending(UUID playerId) {
    PendingSpawn pending = pendingByPlayer.get(playerId);
    return pending != null && !pending.isExpired(System.currentTimeMillis());
  }

  public void markPending(UUID playerId, Vector3d position) {
    long expiresAt = System.currentTimeMillis() + PENDING_TTL_MS;
    pendingByPlayer.put(playerId, new PendingSpawn(position, expiresAt));
  }

  public Integer getActiveMotorcycle(UUID playerId) {
    return activeMotorcycleByPlayer.get(playerId);
  }

  public void clearActive(UUID playerId) {
    Integer networkId = activeMotorcycleByPlayer.remove(playerId);
    if (networkId != null) {
      ownerByNetworkId.remove(networkId);
    }
  }

  public void clearActiveByNetworkId(int networkId) {
    UUID owner = ownerByNetworkId.remove(networkId);
    if (owner != null) {
      activeMotorcycleByPlayer.remove(owner);
    }
  }

  public void assignMotorcycle(UUID playerId, int networkId) {
    activeMotorcycleByPlayer.put(playerId, networkId);
    ownerByNetworkId.put(networkId, playerId);
    logger.atInfo().log("Assigned motorcycle %d to player %s", networkId, playerId);
  }

  public boolean isTracked(int networkId) {
    return ownerByNetworkId.containsKey(networkId);
  }

  public void onMotorcycleSeen(int networkId, Vector3d position) {
    if (ownerByNetworkId.containsKey(networkId)) {
      return;
    }

    long now = System.currentTimeMillis();
    cleanupExpired(now);

    UUID bestPlayer = null;
    double bestDistSq = ASSIGN_RADIUS_SQ;

    if (!pendingByPlayer.isEmpty()) {
      logger.atInfo().log("Motorcycle seen (untracked) at %s. Checking against %d pending requests...", position,
          pendingByPlayer.size());
      System.out.println("TINKERS DEBUG: Motorcycle seen (untracked) at " + position + ". Checking against "
          + pendingByPlayer.size() + " pending requests.");
    }

    for (Map.Entry<UUID, PendingSpawn> entry : pendingByPlayer.entrySet()) {
      PendingSpawn pending = entry.getValue();
      if (pending == null || pending.isExpired(now)) {
        continue;
      }

      double distSq = pending.distanceSq(position);
      logger.atInfo().log(" - Candidate player %s pending pos %s distSq=%.2f (limit %.2f)",
          entry.getKey(), pending.position, distSq, ASSIGN_RADIUS_SQ);
      System.out.println("TINKERS DEBUG: - Candidate player " + entry.getKey() + " pending pos " + pending.position
          + " distSq=" + distSq);

      if (distSq <= bestDistSq) {
        bestDistSq = distSq;
        bestPlayer = entry.getKey();
      }
    }

    if (bestPlayer != null) {
      pendingByPlayer.remove(bestPlayer);
      assignMotorcycle(bestPlayer, networkId);
    } else if (!pendingByPlayer.isEmpty()) {
      logger.atInfo().log("No matching player found for motorcycle at %s", position);
      System.out.println("TINKERS DEBUG: No matching player found for motorcycle at " + position);
    }
  }

  private void cleanupExpired(long now) {
    pendingByPlayer.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isExpired(now));
  }

  private static final class PendingSpawn {
    private final Vector3d position;
    private final long expiresAtMillis;

    private PendingSpawn(Vector3d position, long expiresAtMillis) {
      this.position = position;
      this.expiresAtMillis = expiresAtMillis;
    }

    private boolean isExpired(long nowMillis) {
      return nowMillis > expiresAtMillis;
    }

    private double distanceSq(Vector3d other) {
      double dx = position.x - other.x;
      double dy = position.y - other.y;
      double dz = position.z - other.z;
      return (dx * dx) + (dy * dy) + (dz * dz);
    }
  }
}
