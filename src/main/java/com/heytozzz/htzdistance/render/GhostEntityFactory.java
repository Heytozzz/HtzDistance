package com.heytozzz.htzdistance.render;

import com.heytozzz.htzdistance.HtzDistanceClient;
import com.heytozzz.htzdistance.capture.GhostSnapshot;
import com.heytozzz.htzdistance.ghost.Ghost;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.TagValueInput;
import org.jetbrains.annotations.Nullable;

/**
 * Rebuilds client-only entity instances from snapshots, used purely as render input.
 *
 * <p>The instances get a reference to the client level (constructors require one, and the
 * renderers read light from it) but are <b>never added</b> to it: they are not ticked, cannot be
 * picked by the crosshair and can never be the target of an interaction packet.
 */
public final class GhostEntityFactory {
    /** Next client-only id handed to a ghost entity (render thread only). */
    private static int nextId = -1_000_000;

    private GhostEntityFactory() {
    }

    private static int nextGhostId() {
        int id = nextId--;
        if (nextId > -1_000_000) {
            // Wrapped around after billions of rebuilds: start over.
            nextId = -1_000_000;
        }
        return id;
    }

    /** Returns the cached render entity of the ghost, building it on first use. */
    @Nullable
    public static Entity getOrCreate(Ghost ghost, ClientLevel level) {
        Entity entity = ghost.renderEntity();
        if (entity != null && entity.level() == level) {
            return entity;
        }
        if (ghost.renderEntityFailed()) {
            return null;
        }
        entity = create(ghost.snapshot(), level);
        if (entity == null) {
            ghost.markRenderEntityFailed();
        } else {
            ghost.setRenderEntity(entity);
        }
        return entity;
    }

    @Nullable
    private static Entity create(GhostSnapshot snapshot, ClientLevel level) {
        EntityType<?> type = snapshot.kind().entityType();
        try {
            GhostMapSupport.ensureMapData(level, snapshot.map());
            Entity entity = type.create(level, EntitySpawnReason.LOAD);
            if (entity == null) {
                return null;
            }
            entity.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), snapshot.entityTag()));
            // Renderers read the entity id (26.2+ throws when it is unset). Server ids are
            // positive, so negative ids can never collide; the ghost is never registered anyway.
            entity.setId(nextGhostId());
            // No interpolation from a previous position/rotation: the ghost is static.
            entity.setOldPosAndRot();
            if (entity instanceof LivingEntity living) {
                living.yBodyRotO = living.yBodyRot;
                living.yHeadRotO = living.yHeadRot;
            }
            return entity;
        } catch (RuntimeException e) {
            HtzDistanceClient.LOGGER.warn("Could not rebuild {} ghost at ({}, {}, {})",
                    snapshot.kind(), snapshot.x(), snapshot.y(), snapshot.z(), e);
            return null;
        }
    }
}
