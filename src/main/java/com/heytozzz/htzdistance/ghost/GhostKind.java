package com.heytozzz.htzdistance.ghost;

/**
 * Entity kinds that can be kept as ghosts.
 *
 * <p>Default thresholds come from measurements on the target server: an armor stand disappears
 * when the player stands 16 blocks away from it, an item frame (glow or not) at 8 blocks.
 */
public enum GhostKind {
    ARMOR_STAND("armorStand", 16.0),
    ITEM_FRAME("itemFrame", 8.0),
    GLOW_ITEM_FRAME("glowItemFrame", 8.0);

    private final String configKey;
    private final double defaultThreshold;

    GhostKind(String configKey, double defaultThreshold) {
        this.configKey = configKey;
        this.defaultThreshold = defaultThreshold;
    }

    /** Stable key used in the config file and in persisted ghost data. */
    public String configKey() {
        return configKey;
    }

    /** Default horizontal distance (blocks) at which the server stops tracking this kind. */
    public double defaultThreshold() {
        return defaultThreshold;
    }

    public static GhostKind byConfigKey(String key) {
        for (GhostKind kind : values()) {
            if (kind.configKey.equals(key)) {
                return kind;
            }
        }
        return null;
    }
}
