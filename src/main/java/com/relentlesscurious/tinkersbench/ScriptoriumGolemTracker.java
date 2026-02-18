package com.relentlesscurious.tinkersbench;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the association between an Hourglass block position and the
 * Scriptorium Golem entity that was summoned by it.
 *
 * Also tracks known Golem Book block positions so that adjacency checks
 * (Task 3.1) can be done without querying the world block API.
 */
public class ScriptoriumGolemTracker {

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    private static final File PERSIST_FILE = new File("config", "golem_hourglasses.json");
    private static final Gson GSON = new Gson();

    /**
     * Persists the current set of bound hourglass position keys to disk so they
     * survive server restarts.  Called automatically from bind() and unbind().
     * Failures are non-fatal — they are logged to stderr but do not throw.
     */
    private void savePositions() {
        try {
            PERSIST_FILE.getParentFile().mkdirs();
            try (FileWriter w = new FileWriter(PERSIST_FILE)) {
                GSON.toJson(new ArrayList<>(golemsByBlockPos.keySet()), w);
            }
        } catch (IOException e) {
            System.err.println("TINKERS ERROR [GolemTracker] Failed to save hourglass positions: " + e.getMessage());
        }
    }

    /**
     * Loads persisted hourglass position keys from disk.
     * Returns an empty set if the file does not exist or cannot be read.
     */
    public Set<String> loadPersistedPositions() {
        if (!PERSIST_FILE.exists()) {
            System.out.println("TINKERS DEBUG [GolemTracker] No persisted hourglass file at " + PERSIST_FILE.getAbsolutePath() + " — nothing to restore.");
            return Collections.emptySet();
        }
        try (FileReader r = new FileReader(PERSIST_FILE)) {
            Type listType = new TypeToken<List<String>>() {}.getType();
            List<String> keys = GSON.fromJson(r, listType);
            if (keys == null) return Collections.emptySet();
            System.out.println("TINKERS DEBUG [GolemTracker] Loaded " + keys.size() + " persisted hourglass position(s) from disk.");
            return new HashSet<>(keys);
        } catch (IOException e) {
            System.err.println("TINKERS ERROR [GolemTracker] Failed to read hourglass positions: " + e.getMessage());
            return Collections.emptySet();
        }
    }

    // -------------------------------------------------------------------------
    // In-memory state
    // -------------------------------------------------------------------------

    /** Hourglass pos key → Golem entity Ref */
    private final Map<String, Ref<EntityStore>> golemsByBlockPos = new ConcurrentHashMap<>();

    /** Set of all Golem Book block position keys currently placed in the world */
    private final Set<String> bookPositions = ConcurrentHashMap.newKeySet();

    /**
     * One-shot grace set for Golem Book positions just placed this tick.
     * The Hytale engine fires a BreakBlockEvent immediately after a PlaceBlockEvent
     * at the same position ("break the old block before placing the new one").
     * Consuming from this set in GolemBookBreakSystem suppresses that artifact.
     */
    private final Set<String> bookJustPlacedPositions = ConcurrentHashMap.newKeySet();

    /**
     * One-shot grace set for Hourglass positions just placed this tick.
     * Separate from the book set so HourglassBreakSystem cannot accidentally
     * consume grace entries that belong to GolemBookBreakSystem and vice-versa.
     */
    private final Set<String> hourglassJustPlacedPositions = ConcurrentHashMap.newKeySet();

    // -------------------------------------------------------------------------
    // Internal key helpers
    // -------------------------------------------------------------------------

    /** Convert a block position to a stable string key. */
    private static String key(Vector3i pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    // -------------------------------------------------------------------------
    // Hourglass ↔ Golem bindings
    // -------------------------------------------------------------------------

    /** Record that a golem was summoned at the given hourglass block position. */
    public void bind(Vector3i hourglassPos, Ref<EntityStore> golemRef) {
        golemsByBlockPos.put(key(hourglassPos), golemRef);
        savePositions();
    }

    /** Look up the golem bound to the given hourglass block position, or null. */
    public Ref<EntityStore> getGolem(Vector3i hourglassPos) {
        return golemsByBlockPos.get(key(hourglassPos));
    }

    /** Remove and return the golem entry for the given block position. */
    public Ref<EntityStore> unbind(Vector3i hourglassPos) {
        clearLogs(hourglassPos);
        Ref<EntityStore> ref = golemsByBlockPos.remove(key(hourglassPos));
        savePositions();
        return ref;
    }

    /** True if there is already a summoned golem at this position. */
    public boolean isBound(Vector3i hourglassPos) {
        Ref<EntityStore> ref = golemsByBlockPos.get(key(hourglassPos));
        return ref != null && ref.isValid();
    }

    /**
     * True if the given Ref is one of the golem refs currently tracked by this
     * instance.  Used by GolemNPCPresenceSystem to detect orphaned golems that
     * were persisted by Hytale but are no longer registered in the tracker
     * (e.g. after a restart before GolemRebindSystem ran, or after the associate
     * hourglass was broken in a previous session).
     */
    public boolean isGolemRefKnown(Ref<EntityStore> ref) {
        return golemsByBlockPos.containsValue(ref);
    }

    /** Returns all currently tracked hourglass positions (as Vector3i). */
    public Set<String> getHourglassKeys() {
        return golemsByBlockPos.keySet();
    }

    // -------------------------------------------------------------------------
    // Golem Book position tracking (Task 3.1)
    // -------------------------------------------------------------------------

    /**
     * Register a Golem Book block at the given world position.
     * @return true if the position was newly tracked, false if it was already present
     *         (used to deduplicate multi-entity event firings).
     */
    public boolean addBook(Vector3i bookPos) {
        String k = key(bookPos);
        boolean added = bookPositions.add(k);
        if (added) {
            bookJustPlacedPositions.add(k); // suppress the engine's immediate placement-artifact BreakBlockEvent
        }
        return added;
    }

    /** Remove a Golem Book block at the given world position. */
    public void removeBook(Vector3i bookPos) {
        bookPositions.remove(key(bookPos));
    }

    /**
     * Consume a book placement-grace entry.  Used by GolemBookBreakSystem only.
     * Returns true if the break at this position is a placement artifact and should be skipped.
     */
    public boolean consumePlacementGrace(Vector3i pos) {
        return bookJustPlacedPositions.remove(key(pos));
    }

    /**
     * Mark an hourglass position as just-placed so the engine's immediate
     * placement-artifact BreakBlockEvent is suppressed in HourglassBreakSystem.
     */
    public void markHourglassJustPlaced(Vector3i pos) {
        hourglassJustPlacedPositions.add(key(pos));
    }

    /**
     * Consume an hourglass placement-grace entry.  Used by HourglassBreakSystem only.
     * Kept separate from consumePlacementGrace so that book and hourglass grace entries
     * can never be accidentally consumed by the wrong system.
     */
    public boolean consumeHourglassPlacementGrace(Vector3i pos) {
        return hourglassJustPlacedPositions.remove(key(pos));
    }

    /** True if a Golem Book is tracked at this exact position. */
    public boolean isBook(Vector3i pos) {
        return bookPositions.contains(key(pos));
    }

    /**
     * Returns true if any of the 6 face-adjacent blocks of {@code hourglassPos}
     * contains a tracked Golem Book.
     */
    public boolean isBookAdjacent(Vector3i hourglassPos) {
        int x = hourglassPos.getX();
        int y = hourglassPos.getY();
        int z = hourglassPos.getZ();
        return bookPositions.contains(key(x + 1, y, z))
            || bookPositions.contains(key(x - 1, y, z))
            || bookPositions.contains(key(x, y + 1, z))
            || bookPositions.contains(key(x, y - 1, z))
            || bookPositions.contains(key(x, y, z + 1))
            || bookPositions.contains(key(x, y, z - 1));
    }

    /**
     * If {@code bookPos} is face-adjacent to any tracked hourglass, returns
     * that hourglass position; otherwise returns null.
     */
    public Vector3i findAdjacentHourglass(Vector3i bookPos) {
        int x = bookPos.getX();
        int y = bookPos.getY();
        int z = bookPos.getZ();
        int[][] neighbors = {
            {x + 1, y, z}, {x - 1, y, z},
            {x, y + 1, z}, {x, y - 1, z},
            {x, y, z + 1}, {x, y, z - 1}
        };
        for (int[] n : neighbors) {
            String k = key(n[0], n[1], n[2]);
            if (golemsByBlockPos.containsKey(k)) {
                return new Vector3i(n[0], n[1], n[2]);
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Log buffer (Task 3.2 / 3.3)
    // -------------------------------------------------------------------------

    /**
     * Simple immutable log entry recorded by event-detection systems.
     * Timestamp is a wall-clock ms value; description is a human-readable
     * string.  Task 3.3 will format/persist these entries further.
     */
    public static final class LogEntry {
        public final long timestampMs;
        public final String description;

        public LogEntry(String description) {
            this.timestampMs = System.currentTimeMillis();
            this.description = description;
        }

        @Override
        public String toString() {
            return "[" + timestampMs + "] " + description;
        }
    }

    /** Per-hourglass in-memory log, keyed by hourglass position string. */
    private final Map<String, List<LogEntry>> logsByHourglass = new ConcurrentHashMap<>();

    /**
     * True if this hourglass position has a bound (valid) golem AND at least
     * one adjacent Golem Book — i.e. the monitoring pair is fully active.
     */
    public boolean isActive(Vector3i hourglassPos) {
        return isBound(hourglassPos) && isBookAdjacent(hourglassPos);
    }

    /**
     * Append a log entry for the given hourglass.  No-op if the position is not
     * currently tracked (guards against stale references after unbind).
     */
    public void addLog(Vector3i hourglassPos, String description) {
        String k = key(hourglassPos);
        if (!golemsByBlockPos.containsKey(k)) return;
        logsByHourglass.computeIfAbsent(k, _k -> Collections.synchronizedList(new ArrayList<>()))
                       .add(new LogEntry(description));
    }

    /**
     * Returns a snapshot of all log entries for the given hourglass, oldest first.
     * Returns an empty list if there are no entries or the position is not tracked.
     */
    public List<LogEntry> getLogs(Vector3i hourglassPos) {
        List<LogEntry> entries = logsByHourglass.get(key(hourglassPos));
        if (entries == null) return Collections.emptyList();
        synchronized (entries) {
            return new ArrayList<>(entries);
        }
    }

    /** Remove the log buffer when an hourglass is unbound. */
    private void clearLogs(Vector3i hourglassPos) {
        logsByHourglass.remove(key(hourglassPos));
    }
}
