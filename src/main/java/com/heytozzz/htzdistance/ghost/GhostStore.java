package com.heytozzz.htzdistance.ghost;

import com.heytozzz.htzdistance.capture.GhostSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Ghosts of one server + dimension. Client thread only (the culling worker reads a copy).
 *
 * <p>A flat list is enough: the ghost count is capped (default 1024) and every operation is a
 * single linear pass.
 */
public final class GhostStore {
    private final WorldKey key;
    private final List<Ghost> ghosts = new ArrayList<>();
    /** Bumped on every structural change so readers can detect stale copies. */
    private int revision;

    public GhostStore(WorldKey key) {
        this.key = key;
    }

    public WorldKey key() {
        return key;
    }

    public int size() {
        return ghosts.size();
    }

    public int revision() {
        return revision;
    }

    public List<Ghost> view() {
        return Collections.unmodifiableList(ghosts);
    }

    /**
     * Adds a ghost, replacing any ghost of the same kind within {@code radius} (or with the same
     * UUID): a newer capture of the same entity always wins.
     */
    public Ghost add(GhostSnapshot snapshot, double radius) {
        double radiusSqr = radius * radius;
        ghosts.removeIf(existing -> existing.kind() == snapshot.kind()
                && (existing.snapshot().uuid().equals(snapshot.uuid())
                || existing.snapshot().distanceSqr(snapshot.x(), snapshot.y(), snapshot.z()) <= radiusSqr));
        Ghost ghost = new Ghost(snapshot);
        ghosts.add(ghost);
        revision++;
        return ghost;
    }

    /** Removes ghosts of {@code kind} within {@code radius} of the position. Returns how many. */
    public int removeNear(GhostKind kind, double x, double y, double z, double radius) {
        double radiusSqr = radius * radius;
        return removeIf(ghost -> ghost.kind() == kind && ghost.snapshot().distanceSqr(x, y, z) <= radiusSqr);
    }

    public int removeIf(Predicate<Ghost> predicate) {
        int before = ghosts.size();
        ghosts.removeIf(predicate);
        int removed = before - ghosts.size();
        if (removed > 0) {
            revision++;
        }
        return removed;
    }

    public Iterator<Ghost> iterator() {
        // Structural changes through this iterator must call markChanged().
        return ghosts.iterator();
    }

    public void markChanged() {
        revision++;
    }

    /** Drops the ghosts farthest from the given position until at most {@code max} remain. */
    public int enforceLimit(int max, double px, double py, double pz) {
        if (ghosts.size() <= max) {
            return 0;
        }
        int excess = ghosts.size() - max;
        List<Ghost> byDistance = new ArrayList<>(ghosts);
        byDistance.sort(Comparator.comparingDouble((Ghost g) -> g.snapshot().distanceSqr(px, py, pz)).reversed());
        List<Ghost> toRemove = byDistance.subList(0, excess);
        ghosts.removeAll(toRemove);
        revision++;
        return excess;
    }

    public void invalidateRenderEntities() {
        for (Ghost ghost : ghosts) {
            ghost.invalidateRenderEntity();
        }
    }

    public void clear() {
        if (!ghosts.isEmpty()) {
            ghosts.clear();
            revision++;
        }
    }
}
