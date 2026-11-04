package com.heytozzz.htzdistance.capture;

import com.heytozzz.htzdistance.config.ConfigManager;
import com.heytozzz.htzdistance.config.HtzConfig;
import com.heytozzz.htzdistance.ghost.GhostKind;
import com.heytozzz.htzdistance.ghost.GhostManager;
import com.heytozzz.htzdistance.ghost.GhostStore;

import java.util.OptionalDouble;

/**
 * Decides whether a removed entity left the server's tracking range or was destroyed.
 *
 * <p>Vanilla, Spigot and Paper all check tracking with the horizontal (X/Z) distance between the
 * player and the entity, so only X/Z are used here.
 */
public final class RangeClassifier {
    private RangeClassifier() {
    }

    /**
     * Tracking range in blocks currently used for this kind: the learned range of the current
     * server when learning mode is on and enough samples exist, otherwise the configured one.
     */
    public static double threshold(GhostKind kind) {
        HtzConfig config = ConfigManager.get();
        if (config.learningMode) {
            GhostStore store = GhostManager.get().store();
            OptionalDouble learned = RangeLearner.get().estimate(store == null ? null : store.key().server(), kind);
            if (learned.isPresent()) {
                return learned.getAsDouble();
            }
        }
        return config.settingsFor(kind).threshold;
    }

    /**
     * Horizontal distance below which the player is certainly inside the real tracking range:
     * the threshold minus the configured margin.
     */
    public static double innerRange(GhostKind kind) {
        HtzConfig config = ConfigManager.get();
        return Math.max(0.0, threshold(kind) - config.thresholdMargin);
    }

    /**
     * @param horizontalDistance X/Z distance between player and entity at the moment of removal
     * @return {@code true} if the removal looks like "left tracking range", {@code false} if the
     *         entity was most likely destroyed while the player was close to it
     */
    public static boolean leftTrackingRange(GhostKind kind, double horizontalDistance) {
        return horizontalDistance >= innerRange(kind);
    }

    public static double horizontalDistance(double ax, double az, double bx, double bz) {
        double dx = ax - bx;
        double dz = az - bz;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
