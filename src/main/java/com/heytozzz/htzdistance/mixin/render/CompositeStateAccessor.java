package com.heytozzz.htzdistance.mixin.render;

// Render type internals up to 1.21.10 (1.21.11 replaced them with RenderSetup).
//? if <1.21.11 {
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderType.CompositeState.class)
public interface CompositeStateAccessor {
    @Accessor("textureState")
    RenderStateShard.EmptyTextureStateShard htzdistance$getTextureState();
}
//?}
