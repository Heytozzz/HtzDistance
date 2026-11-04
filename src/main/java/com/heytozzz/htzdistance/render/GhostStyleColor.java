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
        return switch (config.ghostStyle) {
            case NORMAL -> NONE;
            case TINT -> new GhostStyleColor(
                    (config.tintColor >> 16) & 0xFF,
                    (config.tintColor >> 8) & 0xFF,
                    config.tintColor & 0xFF,
                    255,
                    false);
            case TRANSLUCENT -> new GhostStyleColor(255, 255, 255, Math.round(config.translucentAlpha * 255.0f), true);
        };
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
