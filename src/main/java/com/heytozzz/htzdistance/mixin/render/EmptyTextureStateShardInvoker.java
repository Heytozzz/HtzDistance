package com.heytozzz.htzdistance.mixin.render;

// Render type internals up to 1.21.10 (1.21.11 replaced them with RenderSetup).
//? if <1.21.11 {
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
