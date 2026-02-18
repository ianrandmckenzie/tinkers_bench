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
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Phase 4, Task 4.1 — When a player interacts with a placed Golem Book block
 * the formatted log for the adjacent Scribes' Hourglass is displayed in a
 * paged custom UI ({@link GolemBookPage}) rather than as chat messages.
 *
 * The Golem Book's item definition uses "Block_Primary" as its Primary
 * interaction, routing through UseBlockEvent.Pre — the same event used by all
 * block-interaction systems in this plugin.
 *
 * If the book has no adjacent active Hourglass an immediate chat notice is
 * shown instead (nothing meaningful to display in a UI).  If the page engine
 * fails to open, a chat fallback renders the raw log.
 *
 * Diagnostics: All Golem Book interactions are printed to stdout so the server
 * log confirms the block-ID format that triggers this handler.
 */
public class GolemBookReadSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {

    private static final String BOOK_KEY   = "golem_book";
    /** Fallback: maximum entries rendered as chat lines if the custom page fails to open. */
    private static final int    MAX_ENTRIES = 20;

    private final HytaleLogger logger;
    private final ScriptoriumGolemTracker tracker;

    public GolemBookReadSystem(HytaleLogger logger, ScriptoriumGolemTracker tracker) {
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
        String blockId = (blockType != null && blockType.getId() != null) ? blockType.getId() : "";

        // Diagnostic: log all book-related interactions to confirm block ID format
        if (blockId.toLowerCase(Locale.ROOT).contains(BOOK_KEY)) {
            logger.atInfo().log("[GolemBookRead] Golem Book interaction: id='%s' pos=%s", blockId, blockPos);
            System.out.println("TINKERS DEBUG [GolemBookRead] Golem Book interaction: id='" + blockId + "' pos=" + blockPos);
        } else {
            // Not a Golem Book — nothing to do
            return;
        }

        // Resolve the acting player
        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) return;

        // Find the Hourglass adjacent to this book
        Vector3i hgPos = tracker.findAdjacentHourglass(blockPos);
        if (hgPos == null || !tracker.isActive(hgPos)) {
            player.sendMessage(Message.raw(
                "[Scriptorium Golem] This book is not linked to an active Hourglass."));
            logger.atInfo().log("[GolemBookRead] Read attempt at %s — no active adjacent Hourglass.", blockPos);
            System.out.println("TINKERS DEBUG [GolemBookRead] No active Hourglass adjacent to book at " + blockPos);
            return;
        }

        List<ScriptoriumGolemTracker.LogEntry> entries = tracker.getLogs(hgPos);

        logger.atInfo().log("[GolemBookRead] Player '%s' opening log UI for HG@%s (%d entries).",
            player.getDisplayName(), hgPos, entries.size());
        System.out.println("TINKERS DEBUG [GolemBookRead] Player '" + player.getDisplayName()
            + "' opening log UI for HG@" + hgPos + " — " + entries.size() + " entries.");

        // ---- Phase 4: open the paged Golem Book UI ----
        // playerRef (Ref<EntityStore>) was resolved above; getPlayerRef() gives the typed PlayerRef
        PlayerRef playerRefForPage = player.getPlayerRef();
        GolemBookPage page = new GolemBookPage(playerRefForPage, logger, entries, hgPos);
        try {
            player.getPageManager().openCustomPage(playerRef, store, page);
        } catch (Throwable t) {
            logger.atWarning().withCause(t).log("[GolemBookRead] Failed to open GolemBookPage — falling back to chat.");
            System.out.println("TINKERS DEBUG [GolemBookRead] openCustomPage threw: " + t);
            // Chat fallback so the player still gets their data
            if (entries.isEmpty()) {
                player.sendMessage(Message.raw(
                    "[Scriptorium Golem] The Golem Book is blank — no events recorded yet."));
            } else {
                player.sendMessage(Message.raw(
                    "--- Scriptorium Golem Log (Hourglass " + hgPos + ") ---"));
                int start = Math.max(0, entries.size() - MAX_ENTRIES);
                if (start > 0) {
                    player.sendMessage(Message.raw("  ... " + start + " earlier entries omitted ..."));
                }
                for (int i = start; i < entries.size(); i++) {
                    player.sendMessage(Message.raw("  " + entries.get(i).toString()));
                }
                player.sendMessage(Message.raw(
                    "--- End of Log (" + entries.size() + " total entries) ---"));
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
