package com.heytozzz.htzdistance;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Cheap cost counters (nanoseconds) for the expensive parts of the mod, used by the debug log
 * and the performance self-test. Totals only grow; readers compute averages from deltas.
 */
public final class GhostStats {
    /** Time spent capturing removed entities, and the number of captures. */
    public static final AtomicLong CAPTURE_NANOS = new AtomicLong();
    public static final AtomicLong CAPTURES = new AtomicLong();

    /** Time spent per frame selecting and drawing (or extracting) ghosts, and the frame count. */
    public static final AtomicLong RENDER_NANOS = new AtomicLong();
    public static final AtomicLong RENDER_FRAMES = new AtomicLong();

    /** Time spent in culling passes on the worker thread, and the pass count. */
    public static final AtomicLong CULLING_NANOS = new AtomicLong();
    public static final AtomicLong CULLING_PASSES = new AtomicLong();

    private GhostStats() {
    }
}
