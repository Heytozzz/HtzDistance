package com.heytozzz.htzdistance.render;

import com.heytozzz.htzdistance.config.HtzConfig;

/**
 * Per-frame color multipliers derived from the ghost style setting.
 *
 * @param red         red multiplier in [0, 255]
 * @param green       green multiplier in [0, 255]
 * @param blue        blue multiplier in [0, 255]
 * @param alpha       alpha multiplier in [0, 255]
 * @param translucent whether render types must be switched to translucent ones
 */
public record GhostStyleColor(int red, int green, int blue, int alpha, boolean translucent) {
    public static final GhostStyleColor NONE = new GhostStyleColor(255, 255, 255, 255, false);

    public static GhostStyleColor from(HtzConfig config) {
        return switch (config.hologramStyle) {
            case NORMAL -> NONE;
            // Like tinted glass in front of the hologram: each channel is filtered by the tint
            // color, blended with "no filter" (white) by the tint opacity.
            case TINT -> new GhostStyleColor(
                    filter((config.tintColor >> 16) & 0xFF, config.tintOpacity),
                    filter((config.tintColor >> 8) & 0xFF, config.tintOpacity),
                    filter(config.tintColor & 0xFF, config.tintOpacity),
                    255,
                    false);
            case TRANSLUCENT -> new GhostStyleColor(255, 255, 255, Math.round(config.translucentAlpha * 255.0f), true);
        };
    }

    private static int filter(int channel, float opacity) {
        return Math.round(255.0f + (channel - 255.0f) * opacity);
    }

    /** The multipliers as one ARGB color, for vanilla tint arguments. */
    public int argb() {
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    /** Multiplies an ARGB color by this style. */
    public int apply(int argb) {
        return applyAlpha(argb >>> 24) << 24
                | applyRed((argb >> 16) & 0xFF) << 16
                | applyGreen((argb >> 8) & 0xFF) << 8
                | applyBlue(argb & 0xFF);
    }

    public boolean isIdentity() {
        return red == 255 && green == 255 && blue == 255 && alpha == 255;
    }

    public int applyRed(int value) {
        return value * red / 255;
    }

    public int applyGreen(int value) {
        return value * green / 255;
    }

    public int applyBlue(int value) {
        return value * blue / 255;
    }

    public int applyAlpha(int value) {
        return value * alpha / 255;
    }
}
