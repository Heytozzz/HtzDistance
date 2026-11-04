package com.heytozzz.htzdistance.mixin.render;

// Only used up to 1.21.8 (immediate rendering); later versions use render states.
//? if <1.21.10 {
import com.heytozzz.htzdistance.render.GhostRenderTypes;
import com.heytozzz.htzdistance.render.GhostStyleColor;
import com.heytozzz.htzdistance.render.GhostStyleScope;
import com.heytozzz.htzdistance.render.TintingVertexConsumer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the ghost style to buffers requested while ghosts are being drawn (see
 * {@link GhostStyleScope}); does nothing the rest of the time.
 */
@Mixin(MultiBufferSource.BufferSource.class)
public abstract class BufferSourceMixin {

    @ModifyVariable(method = "getBuffer", at = @At("HEAD"), argsOnly = true)
    private RenderType htzdistance$translucentGhostType(RenderType renderType) {
        GhostStyleColor style = GhostStyleScope.active();
        return style != null && style.translucent() ? GhostRenderTypes.translucentFor(renderType) : renderType;
    }

    @Inject(method = "getBuffer", at = @At("RETURN"), cancellable = true)
    private void htzdistance$tintGhostVertices(RenderType renderType, CallbackInfoReturnable<VertexConsumer> cir) {
        GhostStyleColor style = GhostStyleScope.active();
        if (style != null) {
            cir.setReturnValue(new TintingVertexConsumer(cir.getReturnValue(), style));
        }
    }
}
//?}
