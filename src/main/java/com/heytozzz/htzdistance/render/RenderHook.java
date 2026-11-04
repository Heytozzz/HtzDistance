package com.heytozzz.htzdistance.render;

//? if >=26.1 {
/*import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
*///?} else if >=1.21.10 {
/*import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
*///?} else {
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
//?}

/**
 * Connects {@link GhostRenderer} to the world render event of the current Fabric API:
 * the immediate {@code AFTER_ENTITIES} event up to 1.21.8, the extraction event afterwards
 * ({@code world} package on 1.21.10-1.21.11, {@code level} package on 26.x).
 */
public final class RenderHook {
    private RenderHook() {
    }

    //? if >=26.1 {
    /*public static void register() {
        LevelRenderEvents.END_EXTRACTION.register(context -> GhostRenderer.extract(
                context.camera(),
                context.camera().getCullFrustum(),
                context.deltaTracker().getGameTimeDeltaPartialTick(false),
                context.levelState()));
    }
    *///?} else if >=1.21.10 {
    /*public static void register() {
        WorldRenderEvents.END_EXTRACTION.register(context -> GhostRenderer.extract(
                context.camera(),
                context.frustum(),
                context.tickCounter().getGameTimeDeltaPartialTick(false),
                context.worldState()));
    }
    *///?} else {
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
    //?}
}
