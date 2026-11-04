package com.heytozzz.htzdistance.capture;

import com.heytozzz.htzdistance.HtzDistanceClient;
import com.heytozzz.htzdistance.ghost.GhostKind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * Turns live client entities into {@link GhostSnapshot}s.
 */
public final class SnapshotFactory {
    private SnapshotFactory() {
    }

    /** Returns the ghost kind of the entity, or {@code null} if it is not a supported type. */
    @Nullable
    public static GhostKind kindOf(Entity entity) {
        return kindOf(entity.getType());
    }

    @Nullable
    public static GhostKind kindOf(EntityType<?> type) {
        // Compare types, not classes: GlowItemFrame extends ItemFrame.
        if (type == EntityType.ARMOR_STAND) {
            return GhostKind.ARMOR_STAND;
        }
        if (type == EntityType.ITEM_FRAME) {
            return GhostKind.ITEM_FRAME;
        }
        if (type == EntityType.GLOW_ITEM_FRAME) {
            return GhostKind.GLOW_ITEM_FRAME;
        }
        return null;
    }

    /**
     * Captures the full state of the entity. Returns {@code null} if the entity could not be
     * serialized (the error is logged, the game keeps running).
     */
    @Nullable
    public static GhostSnapshot capture(Entity entity, GhostKind kind, ClientLevel level) {
        try {
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            entity.saveWithoutId(output);
            CompoundTag tag = output.buildResult();

            Direction direction = null;
            MapSnapshot map = null;
            if (entity instanceof ItemFrame frame) {
                direction = frame.getDirection();
                map = captureMap(frame.getItem(), level);
            }

            return new GhostSnapshot(
                    kind,
                    entity.getUUID(),
                    entity.getX(),
                    entity.getY(),
                    entity.getZ(),
                    entity.getYRot(),
                    entity.getXRot(),
                    direction,
                    tag,
                    map,
                    System.currentTimeMillis()
            );
        } catch (RuntimeException e) {
            HtzDistanceClient.LOGGER.warn("Could not capture {} at {}", kind, entity.blockPosition(), e);
            return null;
        }
    }

    @Nullable
    private static MapSnapshot captureMap(ItemStack stack, ClientLevel level) {
        MapId mapId = stack.get(DataComponents.MAP_ID);
        if (mapId == null) {
            return null;
        }
        MapItemSavedData data = level.getMapData(mapId);
        if (data == null || data.colors == null || data.colors.length != MapSnapshot.COLOR_COUNT) {
            return null;
        }
        return new MapSnapshot(mapId.id(), data.scale, data.locked, data.colors.clone());
    }
}
