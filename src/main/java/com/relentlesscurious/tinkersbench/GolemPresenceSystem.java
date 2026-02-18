package com.relentlesscurious.tinkersbench;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Task 3.2 (entity proximity detection): Polls every 0.5 s for each player's
 * position.  When a player enters or exits the 15-block radius of an active
 * Golem monitoring pair (Hourglass + adjacent Golem Book), an entry is appended
 * to that hourglass's log.
 *
 * "Active" = hourglass has a valid bound golem AND at least one adjacent book.
 *
 * Tracks which player+hourglass pairs are currently in-range using a composite
 * key so that only the edge transitions ("entered"/"departed") are logged, not
 * every tick.
 *
 * Diagnostics: first proximity poll per player logs the player's current
 * position so we can confirm the Transform API format.
 */
public class GolemPresenceSystem extends DelayedEntitySystem<EntityStore> {

    private static final double RADIUS = 15.0;
    private static final double RADIUS_SQ = RADIUS * RADIUS;

    private final HytaleLogger logger;
    private final ScriptoriumGolemTracker tracker;

    /** Composite "playerRef.hashCode()@hgKey" → true when the player is in-range. */
    private final Set<String> inRangePairs = ConcurrentHashMap.newKeySet();

    /** Players we have logged a diagnostics position message for (dedup). */
    private final Set<Integer> diagnosedPlayers = ConcurrentHashMap.newKeySet();

    /** Monotonic poll counter — used to throttle the "no active hourglasses" warning. */
    private long pollCount = 0;

    public GolemPresenceSystem(HytaleLogger logger, ScriptoriumGolemTracker tracker) {
        super(0.5f); // poll every 0.5 s — fast enough to detect entries without being noisy
        this.logger  = logger;
        this.tracker = tracker;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    @Override
    public void tick(float delta,
                     int index,
                     @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        Ref<EntityStore> playerRef = chunk.getReferenceTo(index);
        if (playerRef == null || !playerRef.isValid()) return;

        // Throttled diagnostic: every 20 polls (~10 s) for this entity, print
        // the active hourglass count so we can confirm setup state in logs.
        long myPoll = ++pollCount;
        if (myPoll % 20 == 1) {
            int hgCount = tracker.getHourglassKeys().size();
            long activeCount = tracker.getHourglassKeys().stream()
                .map(k -> { try { String[] p = k.split(","); return new com.hypixel.hytale.math.vector.Vector3i(Integer.parseInt(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2])); } catch (Exception e) { return null; } })
                .filter(pos -> pos != null && tracker.isActive(pos))
                .count();
            logger.atInfo().log("[GolemPresence] STATE: %d hourglass(es) tracked, %d active", hgCount, activeCount);
            System.out.println("TINKERS DEBUG [GolemPresence] STATE: " + hgCount + " hourglass(es) tracked, " + activeCount + " active");
        }

        TransformComponent tc = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (tc == null) return;
        Vector3d playerPos = tc.getPosition();
        if (playerPos == null) return;

        Player player = store.getComponent(playerRef, Player.getComponentType());
        String playerName = (player != null) ? player.getDisplayName() : ("entity#" + System.identityHashCode(playerRef));

        // One-time diagnostic: confirm transform position format
        int refId = System.identityHashCode(playerRef);
        if (diagnosedPlayers.add(refId)) {
            logger.atInfo().log("[GolemPresence] First poll for player '%s' pos=%s", playerName, playerPos);
            System.out.println("TINKERS DEBUG [GolemPresence] First poll player='" + playerName + "' pos=" + playerPos);
        }

        // Check proximity to every active hourglass
        for (String hgKey : tracker.getHourglassKeys()) {
            Vector3i hgPos = parseKey(hgKey);
            if (hgPos == null) continue;
            if (!tracker.isActive(hgPos)) continue;

            double dx = playerPos.getX() - (hgPos.getX() + 0.5);
            double dy = playerPos.getY() - (hgPos.getY() + 0.5);
            double dz = playerPos.getZ() - (hgPos.getZ() + 0.5);
            double distSq = dx * dx + dy * dy + dz * dz;

            boolean nowInRange = distSq <= RADIUS_SQ;
            String pairKey = refId + "@" + hgKey;
            boolean wasInRange = inRangePairs.contains(pairKey);

            if (nowInRange && !wasInRange) {
                inRangePairs.add(pairKey);
                String msg = "Player '" + playerName + "' entered monitoring radius (dist="
                    + String.format("%.1f", Math.sqrt(distSq)) + ")";
                logger.atInfo().log("[GolemPresence] ENTERED: HG@%s — %s", hgPos, msg);
                System.out.println("TINKERS DEBUG [GolemPresence] ENTERED HG@" + hgPos + " — " + msg);
                tracker.addLog(hgPos, msg);

            } else if (!nowInRange && wasInRange) {
                inRangePairs.remove(pairKey);
                String msg = "Player '" + playerName + "' departed monitoring radius";
                logger.atInfo().log("[GolemPresence] DEPARTED: HG@%s — %s", hgPos, msg);
                System.out.println("TINKERS DEBUG [GolemPresence] DEPARTED HG@" + hgPos + " — " + msg);
                tracker.addLog(hgPos, msg);
            }
        }

        // Clean up any pairs that reference hourglasses no longer tracked
        inRangePairs.removeIf(pair -> {
            String hgPart = pair.contains("@") ? pair.substring(pair.indexOf('@') + 1) : null;
            if (hgPart == null) return true;
            Vector3i hgPos = parseKey(hgPart);
            return hgPos == null || !tracker.isBound(hgPos);
        });
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    /** Parse a "x,y,z" tracker key back into a Vector3i.  Returns null on error. */
    private static Vector3i parseKey(String key) {
        try {
            String[] parts = key.split(",");
            return new Vector3i(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
            );
        } catch (Exception e) {
            return null;
        }
    }
}
