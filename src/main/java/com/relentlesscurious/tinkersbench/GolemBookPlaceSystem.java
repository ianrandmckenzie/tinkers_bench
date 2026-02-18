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
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

/**
 * Task 3.1: Detects Golem Book block placement and registers its position in
 * the tracker.  If the book is face-adjacent to a tracked Hourglass, the
 * hourglass/golem pair is considered "active" and logging may begin.
 *
 * Diagnostics: ALL placements are logged; adjacent-hourglass detection is
 * surfaced as an in-game chat message so the player can confirm without
 * tailing server logs.
 */
public class GolemBookPlaceSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    private static final String BOOK_KEY = "golem_book";

    private final HytaleLogger logger;
    private final ScriptoriumGolemTracker tracker;

    public GolemBookPlaceSystem(HytaleLogger logger, ScriptoriumGolemTracker tracker) {
        super(PlaceBlockEvent.class);
        this.logger  = logger;
        this.tracker = tracker;
    }

    @Override
    public void handle(final int index,
                       @Nonnull final ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull final Store<EntityStore> store,
                       @Nonnull final CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull final PlaceBlockEvent event) {

        ItemStack itemInHand = event.getItemInHand();
        if (itemInHand == null) return;
        String blockId = itemInHand.getItemId();
        if (blockId == null || blockId.isEmpty()) return;

        Vector3i blockPos = event.getTargetBlock();

        // Diagnostic: log every placement (will show book ID format and index)
        logger.atInfo().log("[GolemBookPlace] Block placed: id='%s' pos=%s index=%d", blockId, blockPos, index);
        System.out.println("TINKERS DEBUG [GolemBookPlace] Block placed: id='" + blockId + "' pos=" + blockPos + " index=" + index);

        if (!blockId.toLowerCase(Locale.ROOT).contains(BOOK_KEY)) {
            return;
        }

        // Dedup guard: addBook returns false if this position was already registered.
        // EntityEventSystem can fire handle() once per matching entity in the query;
        // if multiple entities match, we'd process the same placement event N times.
        if (!tracker.addBook(blockPos)) {
            logger.atInfo().log("[GolemBookPlace] Duplicate firing at %s (index=%d) — skipping.", blockPos, index);
            System.out.println("TINKERS DEBUG [GolemBookPlace] Duplicate firing skipped at " + blockPos + " index=" + index);
            return;
        }
        logger.atInfo().log("[GolemBookPlace] Golem Book registered at %s", blockPos);
        System.out.println("TINKERS DEBUG [GolemBookPlace] Golem Book registered at " + blockPos);

        // Check if this book is adjacent to any tracked hourglass
        Vector3i adjacentHourglass = tracker.findAdjacentHourglass(blockPos);

        // Get the placing player for chat feedback
        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        Player player = store.getComponent(playerRef, Player.getComponentType());

        if (adjacentHourglass != null) {
            String msg = "[Golem Book] Adjacent to Hourglass at " + adjacentHourglass + " — logging ACTIVE.";
            logger.atInfo().log("[GolemBookPlace] " + msg);
            System.out.println("TINKERS DEBUG [GolemBookPlace] " + msg);
            if (player != null) {
                player.sendMessage(Message.raw("[Scriptorium Golem] Golem Book placed adjacent to Hourglass — logging now ACTIVE."));
            }
        } else {
            String msg = "[Golem Book] No adjacent Hourglass found — logging INACTIVE.";
            logger.atInfo().log("[GolemBookPlace] " + msg);
            System.out.println("TINKERS DEBUG [GolemBookPlace] " + msg);
            if (player != null) {
                player.sendMessage(Message.raw("[Scriptorium Golem] Golem Book placed — no adjacent Hourglass found, logging INACTIVE."));
            }
        }
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
