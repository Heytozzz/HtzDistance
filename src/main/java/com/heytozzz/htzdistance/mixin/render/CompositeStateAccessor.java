package com.heytozzz.htzdistance.mixin.render;

// Only used up to 1.21.8 (immediate rendering); later versions use render states.
//? if <1.21.10 {
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
