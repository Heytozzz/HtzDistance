package com.heytozzz.htzdistance.render;

import com.heytozzz.htzdistance.mixin.render.CompositeRenderTypeAccessor;
import com.heytozzz.htzdistance.mixin.render.CompositeStateAccessor;
import com.heytozzz.htzdistance.mixin.render.EmptyTextureStateShardInvoker;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Buffer source used for ghosts (immediate rendering, up to 1.21.8). Applies the ghost style:
 * tints vertex colors and, for the translucent style, swaps opaque entity render types for
 * translucent ones with the same texture.
 */
final class GhostBufferSource implements MultiBufferSource {
    /** Opaque render type -> translucent equivalent (or itself when there is none). */
    private static final Map<RenderType, RenderType> TRANSLUCENT_CACHE = new IdentityHashMap<>();

    private final MultiBufferSource delegate;
    private final GhostStyleColor color;

    GhostBufferSource(MultiBufferSource delegate, GhostStyleColor color) {
        this.delegate = delegate;
        this.color = color;
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        RenderType type = color.translucent() ? translucentFor(renderType) : renderType;
        return new TintingVertexConsumer(delegate.getBuffer(type), color);
    }

    private static RenderType translucentFor(RenderType type) {
        return TRANSLUCENT_CACHE.computeIfAbsent(type, GhostBufferSource::computeTranslucent);
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
