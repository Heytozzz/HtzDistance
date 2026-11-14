package com.heytozzz.htzdistance.tools;

import com.heytozzz.htzdistance.GhostStats;
import com.heytozzz.htzdistance.config.ConfigManager;
import com.heytozzz.htzdistance.ghost.Ghost;
import com.heytozzz.htzdistance.ghost.GhostKind;
import com.heytozzz.htzdistance.ghost.GhostManager;
import com.heytozzz.htzdistance.ghost.GhostStore;
import com.heytozzz.htzdistance.render.GhostRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Text lines with the current ghost counts and costs, shown on the F3 screen and by
 * {@code /htzdistance stats}. Costs are averaged over the last full second. Client thread only.
 */
public final class DebugStats {
    private static final long SAMPLE_NANOS = 1_000_000_000L;

    private static long sampleStart = System.nanoTime();
    private static long renderNanos = GhostStats.RENDER_NANOS.get();
    private static long renderFrames = GhostStats.RENDER_FRAMES.get();
    private static long cullingNanos = GhostStats.CULLING_NANOS.get();
    private static long cullingPasses = GhostStats.CULLING_PASSES.get();
    private static double renderMs;
    private static double cullingMs;

    private DebugStats() {
    }

    public static List<String> lines() {
        sample();
        List<String> lines = new ArrayList<>(2);
        GhostStore store = GhostManager.get().store();
        int[] counts = new int[GhostKind.values().length];
        if (store != null) {
            for (Ghost ghost : store.view()) {
                counts[ghost.kind().ordinal()]++;
            }
        }
        int total = store == null ? 0 : store.size();

        String state;
        if (GhostToggle.isHidden()) {
            state = " [off]";
        } else {
            state = "";
        }
        lines.add(String.format(Locale.ROOT,
                "HtzDistance: %d holograms (stands %d, frames %d, glow frames %d, paintings %d), drawn %d%s",
                total,
                counts[GhostKind.ARMOR_STAND.ordinal()],
                counts[GhostKind.ITEM_FRAME.ordinal()],
                counts[GhostKind.GLOW_ITEM_FRAME.ordinal()],
                counts[GhostKind.PAINTING.ordinal()],
                GhostRenderer.lastFrameRendered(),
                state));
        lines.add(String.format(Locale.ROOT, "HtzDistance: render %.3f ms/frame, culling %.3f ms/pass", renderMs, cullingMs));
        return lines;
    }

    /** Refreshes the averages once per second from the cumulative counters. */
    private static void sample() {
        long now = System.nanoTime();
        if (now - sampleStart < SAMPLE_NANOS) {
            return;
        }
        long rn = GhostStats.RENDER_NANOS.get();
        long rf = GhostStats.RENDER_FRAMES.get();
        long cn = GhostStats.CULLING_NANOS.get();
        long cp = GhostStats.CULLING_PASSES.get();
        renderMs = rf > renderFrames ? (rn - renderNanos) / 1_000_000.0 / (rf - renderFrames) : 0.0;
        cullingMs = cp > cullingPasses ? (cn - cullingNanos) / 1_000_000.0 / (cp - cullingPasses) : 0.0;
        renderNanos = rn;
        renderFrames = rf;
        cullingNanos = cn;
        cullingPasses = cp;
        sampleStart = now;
    }
}
