package com.heytozzz.htzdistance.mixin.render;

// Render states (1.21.10+); 1.21.8 styles holograms in BufferSourceMixin.
//? if >=26.1 {
/*import net.minecraft.client.renderer.state.level.CameraRenderState;
*///?} else if >=1.21.10 {
/*import net.minecraft.client.renderer.state.CameraRenderState;
*///?}
//? if >=1.21.10 {
/*import com.heytozzz.htzdistance.render.HologramSubmits;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * Submits hologram render states through {@link HologramSubmits}, which applies the hologram
 * style. Real entities, and holograms with the normal style, are not touched.
 ^/
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void htzdistance$styleHologram(EntityRenderState state, CameraRenderState camera, double x, double y, double z,
                                           PoseStack poseStack, SubmitNodeCollector collector, CallbackInfo ci) {
        SubmitNodeCollector styled = HologramSubmits.wrap(collector, state);
        if (styled != collector) {
            ci.cancel();
            ((EntityRenderDispatcher) (Object) this).submit(state, camera, x, y, z, poseStack, styled);
        }
    }
}
*///?}
