package com.heytozzz.htzdistance.render;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

/**
 * Connects {@link GhostRenderer} to the world render event of the current Fabric API.
 */
public final class RenderHook {
    private RenderHook() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (context.consumers() == null) {
                return;
            }
            GhostRenderer.render(
                    context.camera(),
                    context.frustum(),
                    context.consumers(),
                    context.tickCounter().getGameTimeDeltaPartialTick(false));
        });
    }
}
