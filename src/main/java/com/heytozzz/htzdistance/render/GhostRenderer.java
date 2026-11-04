package com.heytozzz.htzdistance.render;

import com.heytozzz.htzdistance.GhostStats;
import com.heytozzz.htzdistance.HtzDistanceClient;
import com.heytozzz.htzdistance.config.ConfigManager;
import com.heytozzz.htzdistance.config.HtzConfig;
import com.heytozzz.htzdistance.culling.CullingWorker;
import com.heytozzz.htzdistance.ghost.Ghost;
import com.heytozzz.htzdistance.ghost.GhostManager;
import com.heytozzz.htzdistance.ghost.GhostStore;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
//? if <1.21.10 {
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
//?}
//? if >=26.1 {
/*import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
*///?} else if >=1.21.10 {
/*import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
*///?}

import java.util.ArrayList;
import java.util.List;

/**
 * Draws ghosts with the vanilla entity renderers.
 *
 * <p>Up to 1.21.8 entities are drawn immediately into a buffer source, right after the real
 * entities. From 1.21.10 on, rendering is split into extraction and submission: the ghosts'
 * render states are added to the frame's entity list during extraction, and vanilla submits and
 * draws them together with the real entities.
 */
public final class GhostRenderer {
    private static int lastFrameRendered;

    private GhostRenderer() {
    }

    /** Number of ghosts drawn in the last frame (for debugging and tests). */
    public static int lastFrameRendered() {
        return lastFrameRendered;
    }

    /**
     * Ghost entities to draw this frame: enabled, inside the max distance, not occluded and in
     * view. Also publishes the frame state to the culling worker. Render thread only.
     */
    private static List<Entity> visibleGhostEntities(Camera camera, Frustum frustum, float partialTick) {
        List<Entity> result = new ArrayList<>();
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        GhostStore store = GhostManager.get().store();
        HtzConfig config = ConfigManager.get();
        if (level == null || store == null || store.size() == 0 || !config.enabled) {
            // Nothing to draw: stop the culling worker from tracing a stale ghost list.
            CullingWorker.get().clear();
            return result;
        }

        Vec3 cam = camera.position();
        CullingWorker.get().publish(level, cam, store);
        double maxDistanceSqr = config.maxRenderDistance * config.maxRenderDistance;
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        for (Ghost ghost : store.view()) {
            if (config.occlusionCulling && !ghost.isVisible()) {
                continue;
            }
            if (ghost.snapshot().distanceSqr(cam.x, cam.y, cam.z) > maxDistanceSqr) {
                continue;
            }
            Entity entity = GhostEntityFactory.getOrCreate(ghost, level);
            if (entity != null && inView(dispatcher, entity, frustum, cam, partialTick)) {
                result.add(entity);
            }
        }
        return result;
    }

    //? if >=26.3 {
    /*private static boolean inView(EntityRenderDispatcher dispatcher, Entity entity, Frustum frustum, Vec3 cam, float partialTick) {
        return dispatcher.shouldRender(entity, frustum, cam.x, cam.y, cam.z, partialTick);
    }
    *///?} else {
    private static boolean inView(EntityRenderDispatcher dispatcher, Entity entity, Frustum frustum, Vec3 cam, float partialTick) {
        return dispatcher.shouldRender(entity, frustum, cam.x, cam.y, cam.z);
    }
    //?}

    //? if <1.21.10 {
    /** Immediate-mode rendering (1.21.8). */
    public static void render(Camera camera, Frustum frustum, MultiBufferSource buffers, float partialTick) {
        lastFrameRendered = 0;
        long startNanos = System.nanoTime();
        List<Entity> entities = visibleGhostEntities(camera, frustum, partialTick);
        if (entities.isEmpty()) {
            return;
        }
        Vec3 cam = camera.position();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        PoseStack poseStack = new PoseStack();
        int rendered = 0;
        GhostStyleScope.begin(GhostStyleColor.from(ConfigManager.get()));
        try {
            for (Entity entity : entities) {
                try {
                    int light = dispatcher.getPackedLightCoords(entity, partialTick);
                    dispatcher.render(entity,
                            entity.getX() - cam.x, entity.getY() - cam.y, entity.getZ() - cam.z,
                            partialTick, poseStack, buffers, light);
                    rendered++;
                } catch (RuntimeException e) {
                    // Never crash the game because of a ghost.
                    HtzDistanceClient.LOGGER.warn("Could not render ghost {}", entity.getType(), e);
                    hide(entity);
                }
            }
        } finally {
            GhostStyleScope.end();
        }
        lastFrameRendered = rendered;
        GhostStats.RENDER_NANOS.addAndGet(System.nanoTime() - startNanos);
        GhostStats.RENDER_FRAMES.incrementAndGet();
    }
    //?}

    //? if >=1.21.10 {
    /*/^*
     * Adds the ghosts' render states to the frame being extracted. With a non-normal style, the
     * ghosts get a glowing outline in the tint color (render states cannot be tinted directly).
     ^/
    public static void extract(Camera camera, Frustum frustum, float partialTick, LevelRenderState levelState) {
        lastFrameRendered = 0;
        long startNanos = System.nanoTime();
        List<Entity> entities = visibleGhostEntities(camera, frustum, partialTick);
        if (entities.isEmpty()) {
            return;
        }
        HtzConfig config = ConfigManager.get();
        int outline = config.ghostStyle == HtzConfig.GhostStyle.NORMAL ? 0 : 0xFF000000 | config.tintColor;
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        int rendered = 0;
        for (Entity entity : entities) {
            try {
                EntityRenderState state = dispatcher.extractEntity(entity, partialTick);
                if (outline != 0) {
                    state.outlineColor = outline;
                    GlowCompat.markGlowing(levelState);
                }
                levelState.entityRenderStates.add(state);
                rendered++;
            } catch (RuntimeException e) {
                HtzDistanceClient.LOGGER.warn("Could not extract ghost {}", entity.getType(), e);
                hide(entity);
            }
        }
        lastFrameRendered = rendered;
        GhostStats.RENDER_NANOS.addAndGet(System.nanoTime() - startNanos);
        GhostStats.RENDER_FRAMES.incrementAndGet();
    }
    *///?}

    private static void hide(Entity entity) {
        GhostStore store = GhostManager.get().store();
        if (store == null) {
            return;
        }
        for (Ghost ghost : store.view()) {
            if (ghost.renderEntity() == entity) {
                ghost.markRenderEntityFailed();
            }
        }
    }
}
