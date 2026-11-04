package com.heytozzz.htzdistance.render;

import org.jetbrains.annotations.Nullable;

/**
 * Marks the stretch of rendering during which only ghost geometry is produced, so the buffer
 * source mixin can apply the ghost style to exactly those vertices. Render thread only.
 */
public final class GhostStyleScope {
    @Nullable
    private static GhostStyleColor active;

    private GhostStyleScope() {
    }

    /** Style to apply right now, or {@code null} outside ghost rendering. */
    @Nullable
    public static GhostStyleColor active() {
        return active;
    }

    /** Starts styling; a no-op style is not activated at all. */
    public static void begin(GhostStyleColor color) {
        active = color == GhostStyleColor.NONE ? null : color;
    }

    public static void end() {
        active = null;
    }
}
