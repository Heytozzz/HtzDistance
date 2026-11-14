package com.heytozzz.htzdistance.config;

import com.google.gson.annotations.SerializedName;
import com.heytozzz.htzdistance.ghost.GhostKind;

/**
 * Plain config model, serialized as JSON by {@link ConfigManager}.
 *
 * <p>Field names are the JSON keys, so renaming a field breaks existing config files.
 */
public final class HtzConfig {

    /** Settings that exist once per {@link GhostKind}. */
    public static final class KindSettings {
        /** Whether this kind is captured and rendered as a ghost. */
        public boolean enabled = true;
        /** Horizontal distance (blocks) at which the server stops tracking this kind. */
        public double threshold;

        public KindSettings() {
        }

        KindSettings(double threshold) {
            this.threshold = threshold;
        }
    }

    /** How ghosts look compared to real entities. */
    public enum GhostStyle {
        /** Rendered exactly like the real entity. */
        NORMAL,
        /** Rendered as if seen through tinted glass: colors filtered by {@code tintColor}. */
        TINT,
        /** Rendered semi-transparent ({@code translucentAlpha}). */
        TRANSLUCENT
    }

    /** Master switch (also the pause key). Off: the mod does no work at all, holograms are kept. */
    public boolean enabled = true;

    public KindSettings armorStand = new KindSettings(GhostKind.ARMOR_STAND.defaultThreshold());
    public KindSettings itemFrame = new KindSettings(GhostKind.ITEM_FRAME.defaultThreshold());
    public KindSettings glowItemFrame = new KindSettings(GhostKind.GLOW_ITEM_FRAME.defaultThreshold());
    public KindSettings painting = new KindSettings(GhostKind.PAINTING.defaultThreshold());

    /**
     * Tolerance (blocks) around the threshold. A removal counts as "left tracking range" when the
     * horizontal distance is at least {@code threshold - thresholdMargin}; anything closer is
     * treated as destroyed.
     */
    public double thresholdMargin = 1.5;

    /** Max distance (blocks) from the camera at which ghosts are rendered. */
    public double maxRenderDistance = 96.0;

    /** Max number of ghosts kept per dimension. The farthest ones are dropped first. */
    @SerializedName(value = "maxHolograms", alternate = "maxGhosts")
    public int maxHolograms = 1024;

    /**
     * Seconds the player may stay inside the real tracking range of a ghost without the real
     * entity reappearing before the ghost is considered destroyed and removed.
     */
    public double inRangeTimeoutSeconds = 5.0;

    /** Max distance (blocks) between a ghost and a newly added real entity to treat them as the same. */
    public double reconcileRadius = 0.5;

    /** Save ghosts to disk per server and dimension, and load them back on join. */
    public boolean persistence = true;

    /** Skip rendering ghosts hidden behind blocks (asynchronous ray tracing). */
    public boolean occlusionCulling = true;

    @SerializedName(value = "hologramStyle", alternate = "ghostStyle")
    public GhostStyle hologramStyle = GhostStyle.NORMAL;

    /** Tint color as 0xRRGGBB, used when {@link #hologramStyle} is {@link GhostStyle#TINT}. */
    public int tintColor = 0xA0C8FF;

    /** Strength of the tint in [0.05, 1]: 0 would leave the real colors, 1 filters them fully. */
    public float tintOpacity = 0.6f;

    /** Opacity in [0.1, 1], used when {@link #hologramStyle} is {@link GhostStyle#TRANSLUCENT}. */
    public float translucentAlpha = 0.6f;

    /** Glowing outline around holograms (Minecraft 1.21.10+), independent of the style. */
    public boolean glowing = false;

    /** Outline color as 0xRRGGBB, used when {@link #glowing} is on. */
    public int glowColor = 0xA0C8FF;

    /**
     * Reuse ghost render states across frames (Minecraft 1.21.10+): much cheaper with many ghosts.
     * Items are still resolved every frame. Turn off if a mod draws ghosts incorrectly.
     */
    public boolean reuseRenderStates = true;

    /** Learn the real disappearance distance of each kind from observed removals. */
    public boolean learningMode = false;

    /**
     * Set once the mod has switched its F3 entry on (Minecraft 1.21.10+). Kept so that a player
     * who turns the entry off later is not overridden.
     */
    public boolean hologramsDebugEntryEnabled = false;

    /** Log every capture, drop and reconciliation decision. */
    public boolean debugLogging = false;

    public KindSettings settingsFor(GhostKind kind) {
        return switch (kind) {
            case ARMOR_STAND -> armorStand;
            case ITEM_FRAME -> itemFrame;
            case GLOW_ITEM_FRAME -> glowItemFrame;
            case PAINTING -> painting;
        };
    }

    /**
     * Fixes missing or out-of-range values (hand-edited or older files) in place.
     *
     * @return {@code true} if anything was changed
     */
    public boolean sanitize() {
        boolean changed = false;
        for (GhostKind kind : GhostKind.values()) {
            KindSettings settings = settingsFor(kind);
            if (settings == null) {
                settings = new KindSettings(kind.defaultThreshold());
                setSettings(kind, settings);
                changed = true;
            }
            // A missing "threshold" key deserializes as 0: fall back to the default instead of the minimum.
            double threshold = settings.threshold <= 0.0
                    ? kind.defaultThreshold()
                    : clamp(settings.threshold, 1.0, 512.0, kind.defaultThreshold());
            if (threshold != settings.threshold) {
                settings.threshold = threshold;
                changed = true;
            }
        }

        double margin = clamp(thresholdMargin, 0.0, 16.0, 1.5);
        double renderDistance = clamp(maxRenderDistance, 8.0, 1024.0, 96.0);
        int ghosts = maxHolograms <= 0 ? 1024 : Math.min(100_000, maxHolograms);
        double timeout = clamp(inRangeTimeoutSeconds, 0.5, 600.0, 5.0);
        double radius = clamp(reconcileRadius, 0.05, 4.0, 0.5);
        float alpha = (float) clamp(translucentAlpha, 0.1, 1.0, 0.6);
        float tintStrength = (float) clamp(tintOpacity, 0.05, 1.0, 0.6);
        int tint = tintColor & 0xFFFFFF;
        int glow = glowColor & 0xFFFFFF;
        GhostStyle style = hologramStyle == null ? GhostStyle.NORMAL : hologramStyle;

        changed |= margin != thresholdMargin
                || renderDistance != maxRenderDistance
                || ghosts != maxHolograms
                || timeout != inRangeTimeoutSeconds
                || radius != reconcileRadius
                || alpha != translucentAlpha
                || tintStrength != tintOpacity
                || tint != tintColor
                || glow != glowColor
                || style != hologramStyle;

        thresholdMargin = margin;
        maxRenderDistance = renderDistance;
        maxHolograms = ghosts;
        inRangeTimeoutSeconds = timeout;
        reconcileRadius = radius;
        translucentAlpha = alpha;
        tintOpacity = tintStrength;
        tintColor = tint;
        glowColor = glow;
        hologramStyle = style;
        return changed;
    }

    private void setSettings(GhostKind kind, KindSettings settings) {
        switch (kind) {
            case ARMOR_STAND -> armorStand = settings;
            case ITEM_FRAME -> itemFrame = settings;
            case GLOW_ITEM_FRAME -> glowItemFrame = settings;
            case PAINTING -> painting = settings;
        }
    }

    private static double clamp(double value, double min, double max, double fallback) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }
}
