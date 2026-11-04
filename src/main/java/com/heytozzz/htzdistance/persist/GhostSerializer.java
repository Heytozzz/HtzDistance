package com.heytozzz.htzdistance.persist;

import com.heytozzz.htzdistance.capture.GhostSnapshot;
import com.heytozzz.htzdistance.capture.MapSnapshot;
import com.heytozzz.htzdistance.ghost.GhostKind;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Converts {@link GhostSnapshot}s to and from NBT for the on-disk ghost files.
 */
final class GhostSerializer {
    private GhostSerializer() {
    }

    static CompoundTag write(GhostSnapshot snapshot) {
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", snapshot.kind().configKey());
        tag.store("uuid", UUIDUtil.CODEC, snapshot.uuid());
        tag.putDouble("x", snapshot.x());
        tag.putDouble("y", snapshot.y());
        tag.putDouble("z", snapshot.z());
        tag.putFloat("yRot", snapshot.yRot());
        tag.putFloat("xRot", snapshot.xRot());
        if (snapshot.direction() != null) {
            tag.putByte("direction", (byte) snapshot.direction().get3DDataValue());
        }
        tag.put("entity", snapshot.entityTag().copy());
        if (snapshot.map() != null) {
            MapSnapshot map = snapshot.map();
            CompoundTag mapTag = new CompoundTag();
            mapTag.putInt("id", map.mapId());
            mapTag.putByte("scale", map.scale());
            mapTag.putBoolean("locked", map.locked());
            mapTag.putByteArray("colors", map.colors());
            tag.put("map", mapTag);
        }
        tag.putLong("capturedAt", snapshot.capturedAt());
        return tag;
    }

    /** Returns {@code null} for entries that are incomplete or of an unknown kind. */
    @Nullable
    static GhostSnapshot read(CompoundTag tag) {
        GhostKind kind = GhostKind.byConfigKey(tag.getStringOr("kind", ""));
        Optional<UUID> uuid = tag.read("uuid", UUIDUtil.CODEC);
        Optional<CompoundTag> entity = tag.getCompound("entity");
        if (kind == null || uuid.isEmpty() || entity.isEmpty()) {
            return null;
        }

        Direction direction = null;
        byte directionId = tag.getByteOr("direction", (byte) -1);
        if (directionId >= 0 && directionId < 6) {
            direction = Direction.from3DDataValue(directionId);
        }

        MapSnapshot map = tag.getCompound("map").flatMap(mapTag -> mapTag.getByteArray("colors")
                .filter(colors -> colors.length == MapSnapshot.COLOR_COUNT)
                .map(colors -> new MapSnapshot(
                        mapTag.getIntOr("id", 0),
                        mapTag.getByteOr("scale", (byte) 0),
                        mapTag.getBooleanOr("locked", false),
                        colors)))
                .orElse(null);

        return new GhostSnapshot(
                kind,
                uuid.get(),
                tag.getDoubleOr("x", 0.0),
                tag.getDoubleOr("y", 0.0),
                tag.getDoubleOr("z", 0.0),
                tag.getFloatOr("yRot", 0.0f),
                tag.getFloatOr("xRot", 0.0f),
                direction,
                entity.get(),
                map,
                tag.getLongOr("capturedAt", 0L));
    }
}
