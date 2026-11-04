package com.heytozzz.htzdistance.render;

// Only used up to 1.21.8 (immediate rendering); later versions use render states.
//? if <1.21.10 {
import com.heytozzz.htzdistance.mixin.render.CompositeRenderTypeAccessor;
import com.heytozzz.htzdistance.mixin.render.CompositeStateAccessor;
import com.heytozzz.htzdistance.mixin.render.EmptyTextureStateShardInvoker;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Maps opaque entity render types to translucent ones with the same texture, for the
 * translucent ghost style.
 */
public final class GhostRenderTypes {
    /** Opaque render type -> translucent equivalent (or itself when there is none). */
    private static final Map<RenderType, RenderType> TRANSLUCENT_CACHE = new IdentityHashMap<>();

    private GhostRenderTypes() {
    }

    /** Render thread only. */
    public static RenderType translucentFor(RenderType type) {
        return TRANSLUCENT_CACHE.computeIfAbsent(type, GhostRenderTypes::computeTranslucent);
    }

    private static RenderType computeTranslucent(RenderType type) {
        // Only entity-format quads can be swapped safely: other formats (text, lines) either
        // already blend or would be fed vertex elements they do not have.
        if (type.format() != DefaultVertexFormat.NEW_ENTITY || type.mode() != VertexFormat.Mode.QUADS) {
            return type;
        }
        if (!(type instanceof RenderType.CompositeRenderType)) {
            return type;
        }
        RenderType.CompositeState state = ((CompositeRenderTypeAccessor) type).htzdistance$getState();
        Optional<ResourceLocation> texture =
                ((EmptyTextureStateShardInvoker) ((CompositeStateAccessor) (Object) state).htzdistance$getTextureState())
                        .htzdistance$cutoutTexture();
        if (texture.isEmpty() || texture.get().getPath().contains("glint")) {
            return type;
        }
        return RenderType.entityTranslucent(texture.get());
    }
}
//?}
