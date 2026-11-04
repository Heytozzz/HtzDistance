package com.heytozzz.htzdistance.capture;

import com.heytozzz.htzdistance.ghost.GhostKind;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Immutable "photo" of a tracked entity taken right before the client removed it.
 *
 * @param kind       entity kind
 * @param uuid       entity UUID as sent by the server (used only for de-duplication)
 * @param x          position X
 * @param y          position Y
 * @param z          position Z
 * @param yRot       yaw
 * @param xRot       pitch
 * @param direction  facing of hanging entities (item frames), {@code null} for armor stands
 * @param entityTag  full entity state written with {@code Entity#saveWithoutId}
 * @param map        map data when the frame holds a filled map, otherwise {@code null}
 * @param capturedAt wall-clock time of the capture in milliseconds
 */
public record GhostSnapshot(
        GhostKind kind,
        UUID uuid,
        double x,
        double y,
        double z,
        float yRot,
        float xRot,
        @Nullable Direction direction,
        CompoundTag entityTag,
        @Nullable MapSnapshot map,
        long capturedAt
) {
    public double horizontalDistanceSqr(double px, double pz) {
        double dx = px - x;
        double dz = pz - z;
        return dx * dx + dz * dz;
    }

    public double distanceSqr(double px, double py, double pz) {
        double dx = px - x;
        double dy = py - y;
        double dz = pz - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
