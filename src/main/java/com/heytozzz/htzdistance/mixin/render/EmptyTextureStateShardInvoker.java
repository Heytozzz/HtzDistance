package com.heytozzz.htzdistance.mixin.render;

// Only used up to 1.21.8 (immediate rendering); later versions use render states.
//? if <1.21.10 {
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Optional;

@Mixin(RenderStateShard.EmptyTextureStateShard.class)
public interface EmptyTextureStateShardInvoker {
    @Invoker("cutoutTexture")
    Optional<ResourceLocation> htzdistance$cutoutTexture();
}
//?}
