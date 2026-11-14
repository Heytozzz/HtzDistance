package com.heytozzz.htzdistance.mixin.render;

// Render type internals up to 1.21.10 (1.21.11 replaced them with RenderSetup).
//? if <1.21.11 {
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
