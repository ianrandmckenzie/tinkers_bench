package com.relentlesscurious.tinkersbench;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import it.unimi.dsi.fastutil.Pair;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * One-shot startup system: re-spawns Scriptorium Golem NPCs for any hourglass
 * positions that were persisted to disk but lost from in-memory state due to a
 * server restart.
 *
 * Why this approach (file-based) is better than the previous NPC scan:
 *
 *   The previous version queried ALL NPC entities to find Scriptorium Golems.
 *   That means iterating every mob on the server once at startup — expensive,
 *   and the chunk index logic was also fragile across multiple archetype chunks.
 *
 *   ScriptoriumGolemTracker now persists the set of bound hourglass positions to
 *   config/golem_hourglasses.json on every bind() and unbind().  On startup we
 *   read that file — O(k) where k is the number of hourglasses (typically 1-10),
 *   not O(n_npcs).  We then spawn a fresh golem at each unbound position. No NPC
 *   scan required.
 *
 *   If Hytale persists the old golem NPC across restarts, GolemOrphanSystem
 *   detects the now-unbound duplicate and removes it.
 *
 * This system runs once, 2 seconds after startup (to let the world finish
 * loading), then becomes a permanent no-op.
 *
 * Query: PlayerRef — we only need one entity to get a World handle for
 * world.execute().  If no player is online yet the system retries on the next
 * interval until one joins.
 */
public class GolemRebindSystem extends DelayedEntitySystem<EntityStore> {

    private static final String GOLEM_ROLE_ID = "Scriptorium_Golem";

    private final HytaleLogger logger;
    private final ScriptoriumGolemTracker tracker;

    /** Positions that still need to be restored, populated once from disk. */
    private List<Vector3i> pendingPositions = null;

    /** True once all pending spawns have been dispatched. */
    private boolean done = false;

    public GolemRebindSystem(HytaleLogger logger, ScriptoriumGolemTracker tracker) {
        super(2.0f); // wait 2 s for world to load, then retry every 2 s until a player is online
        this.logger  = logger;
        this.tracker = tracker;
    }

    @Override
    public Query<EntityStore> getQuery() {
        // We only need a World reference — a player gives us that.
        return PlayerRef.getComponentType();
    }

    @Override
    public void tick(float delta,
                     int index,
                     @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        if (done) return;

        // Load the persisted positions from disk exactly once.
        if (pendingPositions == null) {
            Set<String> keys = tracker.loadPersistedPositions();
            pendingPositions = new ArrayList<>();
            for (String key : keys) {
                Vector3i pos = parseKey(key);
                if (pos == null) {
                    logger.atWarning().log("[GolemRebind] Could not parse persisted key '%s' — skipping.", key);
                    continue;
                }
                if (tracker.isBound(pos)) {
                    logger.atInfo().log("[GolemRebind] %s already bound — no restore needed.", pos);
                    continue;
                }
                pendingPositions.add(pos);
            }
            logger.atInfo().log("[GolemRebind] %d hourglass position(s) need golem restore.", pendingPositions.size());
            System.out.println("TINKERS DEBUG [GolemRebind] " + pendingPositions.size() + " position(s) need restore.");

            if (pendingPositions.isEmpty()) {
                done = true;
                return;
            }
        }

        // Only need one player to get a World handle; skip other indices.
        if (index != 0) return;

        Ref<EntityStore> playerRef = chunk.getReferenceTo(index);
        if (playerRef == null || !playerRef.isValid()) return;

        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) return;

        World world = player.getWorld();

        // Snapshot the list and mark done before the lambda so that
        // any re-entry (e.g. second player at index 0) is already gated.
        final List<Vector3i> toRestore = new ArrayList<>(pendingPositions);
        pendingPositions.clear();
        done = true;

        world.execute(() -> {
            NPCPlugin npcPlugin = NPCPlugin.get();
            if (npcPlugin == null) {
                logger.atSevere().log("[GolemRebind] NPCPlugin.get() returned null — cannot restore golems.");
                return;
            }

            for (Vector3i hourglassPos : toRestore) {
                // Double-check: something else may have already bound this position
                // (e.g. player placed a new hourglass during the 2-second delay).
                if (tracker.isBound(hourglassPos)) {
                    logger.atInfo().log("[GolemRebind] %s became bound before restore ran — skipping.", hourglassPos);
                    continue;
                }

                Vector3d spawnPos = new Vector3d(
                    hourglassPos.getX() + 0.5,
                    hourglassPos.getY() + 1.0,
                    hourglassPos.getZ() + 0.5
                );

                try {
                    @SuppressWarnings("unchecked")
                    Pair<Ref<EntityStore>, ?> result =
                        npcPlugin.spawnNPC(store, GOLEM_ROLE_ID, null, spawnPos, Vector3f.ZERO);

                    if (result == null) {
                        logger.atWarning().log("[GolemRebind] spawnNPC returned null for hourglass at %s.", hourglassPos);
                        continue;
                    }

                    Ref<EntityStore> golemRef = result.left();
                    if (golemRef == null || !golemRef.isValid()) {
                        logger.atWarning().log("[GolemRebind] Spawned Ref invalid for hourglass at %s.", hourglassPos);
                        continue;
                    }

                    tracker.bind(hourglassPos, golemRef);
                    logger.atInfo().log("[GolemRebind] Golem restored at hourglass %s.", hourglassPos);
                    System.out.println("TINKERS DEBUG [GolemRebind] Golem restored for hourglass at " + hourglassPos);

                } catch (Exception e) {
                    logger.atSevere().log("[GolemRebind] Exception spawning golem for hourglass at %s: %s",
                        hourglassPos, e.getMessage());
                    System.out.println("TINKERS DEBUG [GolemRebind] spawnNPC EXCEPTION for " + hourglassPos + ": " + e);
                }
            }
        });
    }

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
