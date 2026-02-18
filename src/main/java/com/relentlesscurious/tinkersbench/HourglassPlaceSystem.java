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
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import it.unimi.dsi.fastutil.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

/**
 * Task 2.1: Detects Hourglass block placement and summons a Scriptorium Golem
 * at the block's position.
 *
 * Diagnostics strategy: ALL block placements are logged so we can confirm
 * the Hourglass block-type ID format before refining the filter.
 */
public class HourglassPlaceSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    private static final String GOLEM_ROLE_ID = "Scriptorium_Golem";
    private static final String HOURGLASS_KEY  = "hourglass";

    private final HytaleLogger logger;
    private final ScriptoriumGolemTracker tracker;

    public HourglassPlaceSystem(HytaleLogger logger, ScriptoriumGolemTracker tracker) {
        super(PlaceBlockEvent.class);
        this.logger = logger;
        this.tracker = tracker;
    }

    @Override
    public void handle(final int index,
                       @Nonnull final ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull final Store<EntityStore> store,
                       @Nonnull final CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull final PlaceBlockEvent event) {

        // The item being placed is identified via getItemInHand() on PlaceBlockEvent
        ItemStack itemInHand = event.getItemInHand();
        if (itemInHand == null) {
            return;
        }
        String blockId = itemInHand.getItemId();
        if (blockId == null || blockId.isEmpty()) {
            return;
        }
        Vector3i blockPos = event.getTargetBlock();

        // --- Diagnostic log: print every block placement so we learn the ID format ---
        logger.atInfo().log("[HourglassPlace] Block placed: id='%s' pos=%s index=%d", blockId, blockPos, index);
        System.out.println("TINKERS DEBUG [HourglassPlace] Block placed: id='" + blockId + "' pos=" + blockPos + " index=" + index);

        // --- Filter: only act on the Hourglass block ---
        if (!blockId.toLowerCase(Locale.ROOT).contains(HOURGLASS_KEY)) {
            return;
        }

        // --- Guard: already has a golem here (also catches duplicate event firings) ---
        if (tracker.isBound(blockPos)) {
            logger.atInfo().log("[HourglassPlace] Golem already bound at %s (index=%d) — ignoring.", blockPos, index);
            System.out.println("TINKERS DEBUG [HourglassPlace] Duplicate firing / already bound at " + blockPos + " index=" + index);
            return;
        }

        // --- Task 3.1: early check for an already-placed adjacent Golem Book ---
        boolean bookAlreadyAdjacent = tracker.isBookAdjacent(blockPos);
        if (bookAlreadyAdjacent) {
            logger.atInfo().log("[HourglassPlace] Golem Book already adjacent to hourglass at %s — logging will be ACTIVE on spawn.", blockPos);
        }

        // Mark this position so the engine's immediate placement-artifact BreakBlockEvent
        // (fired in the same tick) is suppressed in HourglassBreakSystem.
        tracker.markHourglassJustPlaced(blockPos);

        // --- Build spawn position: centred on block, 1 unit above surface ---
        Vector3d spawnPos = new Vector3d(blockPos.getX() + 0.5, blockPos.getY() + 1.0, blockPos.getZ() + 0.5);

        logger.atInfo().log("[HourglassPlace] Hourglass placed at %s — scheduling golem spawn at %s",
                blockPos, spawnPos);
        System.out.println("TINKERS DEBUG [HourglassPlace] Scheduling golem spawn at " + spawnPos);

        // spawnNPC mutates the store — we must defer it via World.execute() so it
        // runs between ticks, after the store processing lock is released.
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            logger.atWarning().log("[HourglassPlace] Cannot get Player component — skipping spawn.");
            return;
        }

        // Chat confirmation that the hourglass was detected
        player.sendMessage(Message.raw("[Scriptorium Golem] Hourglass detected — summoning Golem..."));

        World world = player.getWorld();
        final Vector3i capturedPos = blockPos;
        final Vector3d capturedSpawnPos = spawnPos;

        world.execute(() -> {
            try {
                NPCPlugin npcPlugin = NPCPlugin.get();
                if (npcPlugin == null) {
                    logger.atWarning().log("[HourglassPlace] NPCPlugin.get() returned null — cannot spawn golem.");
                    return;
                }

                @SuppressWarnings("unchecked")
                Pair<Ref<EntityStore>, ?> result = npcPlugin.spawnNPC(store, GOLEM_ROLE_ID, null, capturedSpawnPos, Vector3f.ZERO);

                if (result == null) {
                    logger.atWarning().log("[HourglassPlace] spawnNPC returned null — spawn may have failed.");
                    return;
                }

                Ref<EntityStore> golemRef = result.left();
                if (golemRef == null || !golemRef.isValid()) {
                    logger.atWarning().log("[HourglassPlace] Spawned Ref is null or invalid — golem did not load.");
                    return;
                }

                tracker.bind(capturedPos, golemRef);
                logger.atInfo().log("[HourglassPlace] Golem summoned and tracked at %s (ref=%s)", capturedPos, golemRef);
                System.out.println("TINKERS DEBUG [HourglassPlace] Golem summoned OK at " + capturedPos);

                // Task 3.1 chat: report whether a book is already adjacent
                // We re-check at execute() time in case the book was placed between events
                boolean bookNow = tracker.isBookAdjacent(capturedPos);
                if (bookNow) {
                    player.sendMessage(Message.raw("[Scriptorium Golem] Golem summoned — Golem Book detected adjacent, logging ACTIVE."));
                } else {
                    player.sendMessage(Message.raw("[Scriptorium Golem] Golem summoned — place a Golem Book adjacent to activate logging."));
                }

            } catch (Exception e) {
                logger.atSevere().log("[HourglassPlace] Exception during deferred spawnNPC: %s", e.getMessage());
                System.out.println("TINKERS DEBUG [HourglassPlace] spawnNPC EXCEPTION: " + e);
            }
        });
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
}
