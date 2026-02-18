package com.relentlesscurious.tinkersbench;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

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
    }

    /** Look up the golem bound to the given hourglass block position, or null. */
    public Ref<EntityStore> getGolem(Vector3i hourglassPos) {
        return golemsByBlockPos.get(key(hourglassPos));
    }

    /** Remove and return the golem entry for the given block position. */
    public Ref<EntityStore> unbind(Vector3i hourglassPos) {
        return golemsByBlockPos.remove(key(hourglassPos));
    }

    /** True if there is already a summoned golem at this position. */
    public boolean isBound(Vector3i hourglassPos) {
        Ref<EntityStore> ref = golemsByBlockPos.get(key(hourglassPos));
        return ref != null && ref.isValid();
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
}
