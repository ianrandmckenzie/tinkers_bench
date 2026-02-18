package com.relentlesscurious.tinkersbench;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;

/**
 * Task 2.2: Detects Hourglass block removal and kills the bound Scriptorium Golem.
 *
 * Diagnostics strategy: ALL block breaks are logged so we can confirm
 * the position format and correlate with HourglassPlaceSystem output.
 * The primary guard is tracker.isBound(pos) — we do not rely on a block ID
 * at break time, which avoids uncertainty about what the BreakBlockEvent exposes.
 */
public class HourglassBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private final HytaleLogger logger;
    private final ScriptoriumGolemTracker tracker;

    public HourglassBreakSystem(HytaleLogger logger, ScriptoriumGolemTracker tracker) {
        super(BreakBlockEvent.class);
        this.logger = logger;
        this.tracker = tracker;
    }

    @Override
    public void handle(final int index,
                       @Nonnull final ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull final Store<EntityStore> store,
                       @Nonnull final CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull final BreakBlockEvent event) {

        Vector3i blockPos = event.getTargetBlock();
        if (blockPos == null) {
            return;
        }

        // --- Diagnostic log: print every block break for positional format confirmation ---
        logger.atInfo().log("[HourglassBreak] Block broken at pos=%s", blockPos);
        System.out.println("TINKERS DEBUG [HourglassBreak] Block broken at pos=" + blockPos);

        // Placement-artifact guard: placing the hourglass fires a BreakBlockEvent at its
        // own position in the same tick — BEFORE the deferred world.execute() bind runs.
        // We must consume the grace FIRST (before isBound), otherwise the golem is not
        // yet bound when the artifact break arrives and the grace is never consumed,
        // silently suppressing the player's real break later.
        if (tracker.consumeHourglassPlacementGrace(blockPos)) {
            logger.atInfo().log("[HourglassBreak] Placement-artifact break suppressed at %s — hourglass stays active.", blockPos);
            System.out.println("TINKERS DEBUG [HourglassBreak] Placement-artifact break suppressed at " + blockPos);
            return;
        }

        // --- Primary guard: only act if a golem is bound at this position ---
        if (!tracker.isBound(blockPos)) {
            return;
        }

        logger.atInfo().log("[HourglassBreak] Hourglass detected broken at %s — scheduling golem despawn.", blockPos);
        System.out.println("TINKERS DEBUG [HourglassBreak] Hourglass broken — despawning golem at " + blockPos);

        // Unbind immediately so duplicate events are ignored
        Ref<EntityStore> golemRef = tracker.unbind(blockPos);
        if (golemRef == null) {
            logger.atWarning().log("[HourglassBreak] Tracker returned null ref after unbind at %s — nothing to despawn.", blockPos);
            return;
        }

        if (!golemRef.isValid()) {
            logger.atInfo().log("[HourglassBreak] Golem ref at %s is already invalid (already dead) — cleanup complete.", blockPos);
            return;
        }

        // We need the Player's World to defer the entity destruction safely,
        // mirroring the deferred-spawn pattern used in HourglassPlaceSystem.
        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            logger.atWarning().log("[HourglassBreak] Cannot get Player component — attempting direct destroy.");
            destroyGolem(commandBuffer, golemRef, blockPos);
            return;
        }

        World world = player.getWorld();
        final Ref<EntityStore> capturedGolemRef = golemRef;
        final Vector3i capturedPos = blockPos;

        world.execute(() -> {
            try {
                if (!capturedGolemRef.isValid()) {
                    logger.atInfo().log("[HourglassBreak] Golem at %s already invalid by the time world.execute() ran.", capturedPos);
                    return;
                }
                commandBuffer.removeEntity(capturedGolemRef, RemoveReason.REMOVE);
                logger.atInfo().log("[HourglassBreak] Golem at %s removed via commandBuffer.removeEntity(REMOVE).", capturedPos);
                System.out.println("TINKERS DEBUG [HourglassBreak] Golem removed OK for hourglass at " + capturedPos);
            } catch (Exception e) {
                logger.atSevere().log("[HourglassBreak] Exception during deferred destroyEntity at %s: %s", capturedPos, e.getMessage());
                System.out.println("TINKERS DEBUG [HourglassBreak] destroyEntity EXCEPTION: " + e);
            }
        });
    }

    /** Fallback: attempt destroy directly on the commandBuffer (no world.execute wrapper). */
    private void destroyGolem(CommandBuffer<EntityStore> commandBuffer, Ref<EntityStore> golemRef, Vector3i pos) {
        try {
            commandBuffer.removeEntity(golemRef, RemoveReason.REMOVE);
            logger.atInfo().log("[HourglassBreak] Golem at %s removed directly (no world context).", pos);
            System.out.println("TINKERS DEBUG [HourglassBreak] Golem destroyed directly at " + pos);
        } catch (Exception e) {
            logger.atSevere().log("[HourglassBreak] Exception during direct destroyEntity at %s: %s", pos, e.getMessage());
            System.out.println("TINKERS DEBUG [HourglassBreak] direct destroyEntity EXCEPTION: " + e);
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
