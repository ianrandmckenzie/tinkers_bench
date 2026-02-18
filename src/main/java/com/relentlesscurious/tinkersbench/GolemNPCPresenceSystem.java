package com.relentlesscurious.tinkersbench;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Task 3.2 (NPC proximity detection): Polls every 0.5 s for each NPC entity's
 * position.  When an NPC enters or exits the 15-block radius of an active Golem
 * monitoring pair (Hourglass + adjacent Golem Book), an entry is appended to
 * that hourglass's log.
 *
 * Uses NPCEntity.getComponentType() as the archetype query so only proper NPC
 * entities (Kweebec, sheep, etc.) are iterated — vehicles and other non-NPC
 * entities don't carry this component.
 *
 * Mirrors GolemPresenceSystem but for NPCs instead of players.
 */
public class GolemNPCPresenceSystem extends DelayedEntitySystem<EntityStore> {

    private static final double RADIUS = 15.0;
    private static final double RADIUS_SQ = RADIUS * RADIUS;

    private final HytaleLogger logger;
    private final ScriptoriumGolemTracker tracker;

    /** Composite "refId@hgKey" → true when the NPC is in-range of that hourglass. */
    private final Set<String> inRangePairs = ConcurrentHashMap.newKeySet();

    /** NPCs we have already logged a first-seen diagnostics line for. */
    private final Set<Integer> diagnosedNPCs = ConcurrentHashMap.newKeySet();

    /**
     * Refs of orphaned Scriptorium_Golem NPCs that were removed by this system.
     * Tracked to avoid logging the same removal more than once.
     */
    private final Set<Integer> removedOrphans = ConcurrentHashMap.newKeySet();

    /** Ticks since startup — used to delay orphan cleanup until GolemRebindSystem
     *  has had a chance to run (it fires at the 2 s mark; we wait 3 polling
     *  intervals = 1.5 s, well within the 2 s window, so we add an extra guard). */
    private int pollsSinceStartup = 0;
    private static final int ORPHAN_CLEANUP_DELAY_POLLS = 8; // ~4 s at 0.5 s cadence

    public GolemNPCPresenceSystem(HytaleLogger logger, ScriptoriumGolemTracker tracker) {
        super(0.5f); // poll every 0.5 s, same cadence as player presence
        this.logger  = logger;
        this.tracker = tracker;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return NPCEntity.getComponentType();
    }

    @Override
    public void tick(float delta,
                     int index,
                     @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        Ref<EntityStore> npcRef = chunk.getReferenceTo(index);
        if (npcRef == null || !npcRef.isValid()) return;

        TransformComponent tc = store.getComponent(npcRef, TransformComponent.getComponentType());
        if (tc == null) return;
        Vector3d npcPos = tc.getPosition();
        if (npcPos == null) return;

        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        String npcName = (npc != null && npc.getRoleName() != null) ? npc.getRoleName() : "NPC";

        int refId = System.identityHashCode(npcRef);

        // One-time diagnostic per NPC
        if (diagnosedNPCs.add(refId)) {
            logger.atInfo().log("[GolemNPCPresence] First poll for NPC '%s' pos=%s", npcName, npcPos);
            System.out.println("TINKERS DEBUG [GolemNPCPresence] First poll NPC='" + npcName + "' pos=" + npcPos);
        }

        // Orphan cleanup: if this is a Scriptorium_Golem NPC that is not bound in the
        // tracker after the startup grace period, it is a stale persisted entity left
        // over from a previous server run.  Remove it so the world doesn't accumulate
        // duplicate golems when GolemRebindSystem spawns fresh ones.
        if (index == 0) pollsSinceStartup++;
        if ("Scriptorium_Golem".equals(npcName)
                && pollsSinceStartup > ORPHAN_CLEANUP_DELAY_POLLS
                && !tracker.isGolemRefKnown(npcRef)
                && !removedOrphans.contains(refId)) {
            removedOrphans.add(refId);
            logger.atWarning().log("[GolemNPCPresence] Orphaned Scriptorium_Golem NPC detected (ref=%s) — removing.", npcRef);
            System.out.println("TINKERS DEBUG [GolemNPCPresence] Removing orphaned golem ref=" + npcRef);
            commandBuffer.removeEntity(npcRef, RemoveReason.REMOVE);
            return;
        }

        // Check proximity to every active hourglass
        for (String hgKey : tracker.getHourglassKeys()) {
            Vector3i hgPos = parseKey(hgKey);
            if (hgPos == null) continue;
            if (!tracker.isActive(hgPos)) continue;

            double dx = npcPos.getX() - (hgPos.getX() + 0.5);
            double dy = npcPos.getY() - (hgPos.getY() + 0.5);
            double dz = npcPos.getZ() - (hgPos.getZ() + 0.5);
            double distSq = dx * dx + dy * dy + dz * dz;

            boolean nowInRange = distSq <= RADIUS_SQ;
            String pairKey = refId + "@" + hgKey;
            boolean wasInRange = inRangePairs.contains(pairKey);

            if (nowInRange && !wasInRange) {
                inRangePairs.add(pairKey);
                String msg = "NPC '" + npcName + "' entered monitoring radius (dist="
                    + String.format("%.1f", Math.sqrt(distSq)) + ")";
                logger.atInfo().log("[GolemNPCPresence] ENTERED: HG@%s — %s", hgPos, msg);
                System.out.println("TINKERS DEBUG [GolemNPCPresence] ENTERED HG@" + hgPos + " — " + msg);
                tracker.addLog(hgPos, msg);

            } else if (!nowInRange && wasInRange) {
                inRangePairs.remove(pairKey);
                String msg = "NPC '" + npcName + "' departed monitoring radius";
                logger.atInfo().log("[GolemNPCPresence] DEPARTED: HG@%s — %s", hgPos, msg);
                System.out.println("TINKERS DEBUG [GolemNPCPresence] DEPARTED HG@" + hgPos + " — " + msg);
                tracker.addLog(hgPos, msg);
            }
        }

        // Clean up stale pairs for hourglasses that have since been unbound
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
