package com.heytozzz.htzdistance.mixin.render;

// Only used up to 1.21.8 (immediate rendering); later versions use render states.
//? if <1.21.10 {
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read access to the render state of composite render types (used for translucent ghosts). */
@Mixin(RenderType.CompositeRenderType.class)
public interface CompositeRenderTypeAccessor {
    @Accessor("state")
    RenderType.CompositeState htzdistance$getState();
}
//?}
