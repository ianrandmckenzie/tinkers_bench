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
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;

/**
 * Task 3.1 (cleanup): Detects Golem Book block removal and deregisters its
 * position from the tracker, deactivating logging for any paired Hourglass.
 */
public class GolemBookBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private final HytaleLogger logger;
    private final ScriptoriumGolemTracker tracker;

    public GolemBookBreakSystem(HytaleLogger logger, ScriptoriumGolemTracker tracker) {
        super(BreakBlockEvent.class);
        this.logger  = logger;
        this.tracker = tracker;
    }

    @Override
    public void handle(final int index,
                       @Nonnull final ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull final Store<EntityStore> store,
                       @Nonnull final CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull final BreakBlockEvent event) {

        Vector3i blockPos = event.getTargetBlock();
        if (blockPos == null) return;

        // Diagnostic log for every break (we'll see position format)
        logger.atInfo().log("[GolemBookBreak] Block broken at pos=%s", blockPos);
        System.out.println("TINKERS DEBUG [GolemBookBreak] Block broken at pos=" + blockPos);

        // If this position isn't a tracked book, nothing to do
        if (!tracker.isBook(blockPos)) {
            return;
        }

        // Placement-artifact guard: the Hytale engine fires a BreakBlockEvent at the
        // placed position immediately after every PlaceBlockEvent ("break old block first").
        // If addBook was just called for this position, this is that artifact — skip it.
        if (tracker.consumePlacementGrace(blockPos)) {
            logger.atInfo().log("[GolemBookBreak] Placement-artifact break suppressed at %s — book stays active.", blockPos);
            System.out.println("TINKERS DEBUG [GolemBookBreak] Placement-artifact break suppressed at " + blockPos);
            return;
        }
        // Find adjacency BEFORE removing (order matters)
        Vector3i adjacentHourglass = tracker.findAdjacentHourglass(blockPos);
        tracker.removeBook(blockPos);

        if (adjacentHourglass == null) {
            // Not a book adjacent to any hourglass — skip player notification
            return;
        }

        logger.atInfo().log("[GolemBookBreak] Golem Book removed at %s — Hourglass at %s logging now INACTIVE.",
                blockPos, adjacentHourglass);
        System.out.println("TINKERS DEBUG [GolemBookBreak] Book removed, logging deactivated for HG at " + adjacentHourglass);

        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player != null) {
            player.sendMessage(Message.raw(
                "[Scriptorium Golem] Golem Book removed — logging INACTIVE for Hourglass at " + adjacentHourglass + "."));
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
