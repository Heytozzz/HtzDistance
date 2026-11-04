package com.heytozzz.htzdistance.render;

import com.heytozzz.htzdistance.HtzDistanceClient;
import com.heytozzz.htzdistance.config.ConfigManager;
import com.heytozzz.htzdistance.culling.CullingWorker;
import com.heytozzz.htzdistance.config.HtzConfig;
import com.heytozzz.htzdistance.ghost.Ghost;
import com.heytozzz.htzdistance.ghost.GhostManager;
import com.heytozzz.htzdistance.ghost.GhostStore;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Draws ghosts with the vanilla entity renderers, right after the real entities.
 */
public final class GhostRenderer {
    private static int lastFrameRendered;

    private GhostRenderer() {
    }

    /** Number of ghosts drawn in the last frame (for debugging and tests). */
    public static int lastFrameRendered() {
        return lastFrameRendered;
    }

    /** Immediate-mode rendering (1.21.8 and older render pipelines). */
    public static void render(Camera camera, Frustum frustum, MultiBufferSource buffers, float partialTick) {
        lastFrameRendered = 0;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        GhostStore store = GhostManager.get().store();
        HtzConfig config = ConfigManager.get();
        if (level == null || store == null || store.size() == 0 || !config.enabled) {
            return;
        }

        Vec3 cam = camera.getPosition();
        CullingWorker.get().publish(level, cam, store);
        double maxDistanceSqr = config.maxRenderDistance * config.maxRenderDistance;
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        GhostStyleColor color = GhostStyleColor.from(config);
        MultiBufferSource source = color == GhostStyleColor.NONE ? buffers : new GhostBufferSource(buffers, color);
        PoseStack poseStack = new PoseStack();

        int rendered = 0;
        for (Ghost ghost : store.view()) {
            if (config.occlusionCulling && !ghost.isVisible()) {
                continue;
            }
            if (ghost.snapshot().distanceSqr(cam.x, cam.y, cam.z) > maxDistanceSqr) {
                continue;
            }
            Entity entity = GhostEntityFactory.getOrCreate(ghost, level);
            if (entity == null || !dispatcher.shouldRender(entity, frustum, cam.x, cam.y, cam.z)) {
                continue;
            }
            try {
                int light = dispatcher.getPackedLightCoords(entity, partialTick);
                dispatcher.render(entity,
                        entity.getX() - cam.x, entity.getY() - cam.y, entity.getZ() - cam.z,
                        partialTick, poseStack, source, light);
                rendered++;
            } catch (RuntimeException e) {
                // Never crash the game because of a ghost; stop drawing this one.
                HtzDistanceClient.LOGGER.warn("Could not render {} ghost, hiding it", ghost.kind(), e);
                ghost.markRenderEntityFailed();
            }
        }
        lastFrameRendered = rendered;
    }
}
