package com.relentlesscurious.tinkersbench;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;

/**
 * Task 3.2 (block interaction detection): Listens for UseBlockEvent.Pre fired
 * on players.  When the interacting player is within RADIUS blocks of an active
 * Golem monitoring pair (Hourglass + adjacent Golem Book), the event is
 * classified and appended to the hourglass log.
 *
 * "Active" = hourglass has a valid bound golem AND at least one adjacent book.
 *
 * Detected events:
 *   - Generic block interaction (catches chests, doors, levers, etc. via block
 *     type ID keywords; extended to more specific keywords as we confirm IDs).
 *
 * Diagnostics: ALL UseBlockEvent fires are logged to help confirm block ID
 * format before we refine filters further.
 */
public class GolemSightEventSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {

    private static final double RADIUS = 15.0;
    private static final double RADIUS_SQ = RADIUS * RADIUS;

    private final HytaleLogger logger;
    private final ScriptoriumGolemTracker tracker;

    public GolemSightEventSystem(HytaleLogger logger, ScriptoriumGolemTracker tracker) {
        super(UseBlockEvent.Pre.class);
        this.logger  = logger;
        this.tracker = tracker;
    }

    @Override
    public void handle(final int index,
                       @Nonnull final ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull final Store<EntityStore> store,
                       @Nonnull final CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull final UseBlockEvent.Pre event) {

        Vector3i blockPos = event.getTargetBlock();
        if (blockPos == null) return;

        BlockType blockType = event.getBlockType();
        String blockId = (blockType != null && blockType.getId() != null) ? blockType.getId() : "unknown";

        // --- Diagnostic: log all block interactions to learn ID format ---
        logger.atInfo().log("[GolemSight] UseBlock: id='%s' pos=%s", blockId, blockPos);
        System.out.println("TINKERS DEBUG [GolemSight] UseBlock: id='" + blockId + "' pos=" + blockPos);

        // Get the acting player's position for distance checks
        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        TransformComponent tc = store.getComponent(playerRef, TransformComponent.getComponentType());
        Player player = store.getComponent(playerRef, Player.getComponentType());
        String actorName = (player != null) ? player.getDisplayName() : "Unknown";

        if (tc == null) return;
        Vector3d actorPos = tc.getPosition();
        if (actorPos == null) return;

        // Check all active hourglasses for proximity
        for (String hgKey : tracker.getHourglassKeys()) {
            Vector3i hgPos = parseKey(hgKey);
            if (hgPos == null) continue;
            if (!tracker.isActive(hgPos)) continue;

            double dx = actorPos.getX() - (hgPos.getX() + 0.5);
            double dy = actorPos.getY() - (hgPos.getY() + 0.5);
            double dz = actorPos.getZ() - (hgPos.getZ() + 0.5);
            double distSq = dx * dx + dy * dy + dz * dz;

            if (distSq > RADIUS_SQ) continue;

            // Classify the interaction
            String eventType = classifyBlockInteraction(blockId);
            String logMsg = actorName + " " + eventType + " [" + blockId + "] at " + blockPos
                + " (dist=" + String.format("%.1f", Math.sqrt(distSq)) + ")";

            logger.atInfo().log("[GolemSight] LOGGED for HG@%s — %s", hgPos, logMsg);
            System.out.println("TINKERS DEBUG [GolemSight] LOGGED for HG@" + hgPos + " — " + logMsg);

            tracker.addLog(hgPos, logMsg);
        }
    }

    /**
     * Returns a human-readable interaction description based on block ID keywords.
     * As actual block IDs are confirmed in server logs, this method can be refined.
     */
    private static String classifyBlockInteraction(String blockId) {
        String lower = blockId.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("chest") || lower.contains("crate") || lower.contains("storage")) {
            return "opened/interacted with a chest";
        }
        if (lower.contains("door") || lower.contains("gate")) {
            return "opened/closed a door";
        }
        if (lower.contains("lever") || lower.contains("button") || lower.contains("switch")) {
            return "activated a switch";
        }
        if (lower.contains("furnace") || lower.contains("forge") || lower.contains("smelter")) {
            return "used a furnace";
        }
        return "interacted with a block";
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Collections.singleton(RootDependency.first());
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    /** Parse a "x,y,z" tracker key back into a Vector3i.  Returns null on error. */
    @Nullable
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
