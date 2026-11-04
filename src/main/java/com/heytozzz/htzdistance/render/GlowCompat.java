package com.heytozzz.htzdistance.render;

//? if >=26.1 {
/*import net.minecraft.client.renderer.state.level.LevelRenderState;
*///?} else if >=1.21.10 {
/*import net.minecraft.client.renderer.state.LevelRenderState;
*///?}

/**
 * Version differences around entity outlines (used to highlight ghosts on 1.21.10+).
 */
final class GlowCompat {
    private GlowCompat() {
    }

    //? if >=1.21.10 <26.2 {
    /*/^* Outlines are only drawn when the frame is flagged as having glowing entities. ^/
    static void markGlowing(LevelRenderState levelState) {
        levelState.haveGlowingEntities = true;
    }
    *///?} else if >=26.2 {
    /*/^* The frame flag no longer exists; a non-zero outline color is enough. ^/
    static void markGlowing(LevelRenderState levelState) {
    }
    *///?}
}
