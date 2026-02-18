package com.relentlesscurious.tinkersbench;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Phase 4, Task 4.1 — Golem Book paged log UI.
 *
 * Opened by {@link GolemBookReadSystem} when a player interacts with a placed
 * Golem Book block that is adjacent to an active Scribes' Hourglass.
 *
 * Displays up to PAGE_SIZE log entries per page.  Prev / Next buttons
 * navigate through the full log.  Close dismisses the page.  If no events
 * have been recorded, a "blank book" message is shown instead.
 *
 * Layout: Pages/TinkersBench_GolemBookPage.ui
 */
public class GolemBookPage extends InteractiveCustomUIPage<GolemBookPage.PageData> {

    /** Number of log entries shown per page. */
    private static final int PAGE_SIZE = 10;

    private final HytaleLogger logger;
    private final List<ScriptoriumGolemTracker.LogEntry> entries;
    private final Vector3i hgPos;

    /** Current page index (0-based). */
    private int currentPage = 0;

    public GolemBookPage(@Nonnull PlayerRef playerRef,
                         @Nonnull HytaleLogger logger,
                         @Nonnull List<ScriptoriumGolemTracker.LogEntry> entries,
                         @Nonnull Vector3i hgPos) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.logger  = logger;
        this.entries = entries;
        this.hgPos   = hgPos;
    }

    // -------------------------------------------------------------------------
    // Build
    // -------------------------------------------------------------------------

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {

        cmd.append("Pages/TinkersBench_GolemBookPage.ui");

        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) PAGE_SIZE));
        // Guard against entries shrinking across pages (e.g. log cleared at runtime)
        if (currentPage >= totalPages) currentPage = totalPages - 1;

        // Header detail
        cmd.set("#HGPos.Text", "Hourglass @ " + hgPos);
        cmd.set("#PageLabel.Text", "Page " + (currentPage + 1) + " / " + totalPages);

        // Populate entry list
        cmd.clear("#EntryList");
        if (entries.isEmpty()) {
            cmd.appendInline("#EntryList",
                "Group { Padding: (Left: 8, Top: 8); " +
                "Label { Text: \"The Golem Book is blank — no events recorded yet.\"; " +
                "Style: (TextColor: #888888, FontSize: 13); } }");
        } else {
            int start = currentPage * PAGE_SIZE;
            int end   = Math.min(start + PAGE_SIZE, entries.size());
            for (int i = start; i < end; i++) {
                // Escape double-quotes so the inline UI string stays valid
                String text = entries.get(i).toString().replace("\"", "'");
                cmd.appendInline("#EntryList",
                    "Group { LayoutMode: Left; Anchor: (Left: 0, Right: 0); " +
                    "Padding: (Left: 8, Top: 2, Bottom: 2); " +
                    "Label { Text: \"" + text + "\"; " +
                    "Style: (FontSize: 12, TextColor: #dddddd); FlexWeight: 1; } }");
            }
        }

        // Navigation button visibility
        cmd.set("#PrevButton.Visible", currentPage > 0);
        cmd.set("#NextButton.Visible", currentPage < totalPages - 1);

        // Event bindings
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PrevButton",
            EventData.of("Action", "PrevPage"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NextButton",
            EventData.of("Action", "NextPage"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of("Action", "Close"), false);

        logger.atInfo().log("[GolemBookPage] Built page %d/%d for HG@%s (%d total entries).",
            currentPage + 1, totalPages, hgPos, entries.size());
    }

    // -------------------------------------------------------------------------
    // Event handling
    // -------------------------------------------------------------------------

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull PageData data) {
        if (data.action == null) return;

        switch (data.action) {
            case "Close":
                this.close();
                return;

            case "PrevPage":
                if (currentPage > 0) {
                    currentPage--;
                    this.sendUpdate();
                }
                return;

            case "NextPage": {
                int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) PAGE_SIZE));
                if (currentPage < totalPages - 1) {
                    currentPage++;
                    this.sendUpdate();
                }
                return;
            }

            default:
                logger.atWarning().log("[GolemBookPage] Unknown UI action received: '%s'", data.action);
        }
    }

    // -------------------------------------------------------------------------
    // Data codec
    // -------------------------------------------------------------------------

    public static final class PageData {

        public static final BuilderCodec<PageData> CODEC =
            BuilderCodec.builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING),
                    (data, s) -> data.action = s, data -> data.action)
                .add()
                .build();

        private String action;
    }
}
