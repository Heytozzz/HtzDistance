package com.heytozzz.htzdistance.render;

//? if <1.21.11 {
import com.heytozzz.htzdistance.mixin.render.CompositeRenderTypeAccessor;
import com.heytozzz.htzdistance.mixin.render.CompositeStateAccessor;
import com.heytozzz.htzdistance.mixin.render.EmptyTextureStateShardInvoker;
import net.minecraft.client.renderer.RenderType;
//?} else {
/*import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import java.lang.reflect.Field;
*///?}
import net.minecraft.resources.ResourceLocation;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Maps opaque entity and item render types to translucent ones with the same texture, for the
 * translucent hologram style. Types that cannot be swapped safely (text, lines, glint, outlines,
 * other vertex formats) are returned unchanged.
 */
public final class GhostRenderTypes {
    /** Opaque render type -> translucent equivalent (or itself when there is none). */
    private static final Map<RenderType, RenderType> TRANSLUCENT_CACHE = new IdentityHashMap<>();

    private GhostRenderTypes() {
    }

    /** Render thread only. */
    public static RenderType translucentFor(RenderType type) {
        RenderType cached = TRANSLUCENT_CACHE.get(type);
        if (cached == null) {
            try {
                cached = computeTranslucent(type);
            } catch (RuntimeException | ReflectiveOperationException e) {
                cached = type;
            }
            TRANSLUCENT_CACHE.put(type, cached);
        }
        return cached;
    }

    private static RenderType computeTranslucent(RenderType type) throws ReflectiveOperationException {
        if (type.isOutline()) {
            return type;
        }
        Optional<ResourceLocation> texture = texture(type);
        if (texture.isEmpty() || texture.get().getPath().contains("glint")) {
            return type;
        }
        // Culled variants first: hidden faces would show through a translucent hologram. Block
        // sheet types (26.1+) only match when they use the same atlas.
        for (RenderType candidate : candidates(texture.get())) {
            // Same vertex layout, or the renderer would feed elements the type does not have.
            if (candidate.format() == type.format() && texture(candidate).equals(texture)) {
                return candidate;
            }
        }
        return type;
    }

    //? if >=26.3 {
    /*private static List<RenderType> candidates(ResourceLocation texture) {
        return List.of(RenderTypes.entityTranslucentCull(texture), RenderTypes.itemTranslucent(texture), RenderTypes.entityTranslucent(texture),
                Sheets.translucentBlockItemSheet(), RenderTypes.translucentMovingBlock());
    }
    *///?} else if >=26.1 {
    /*private static List<RenderType> candidates(ResourceLocation texture) {
        return List.of(RenderTypes.itemTranslucent(texture), RenderTypes.entityTranslucent(texture),
                Sheets.translucentBlockItemSheet(), RenderTypes.translucentMovingBlock());
    }
    *///?} else if >=1.21.11 {
    /*private static List<RenderType> candidates(ResourceLocation texture) {
        return List.of(RenderTypes.itemEntityTranslucentCull(texture), RenderTypes.entityTranslucent(texture));
    }
    *///?} else {
    private static List<RenderType> candidates(ResourceLocation texture) {
        return List.of(RenderType.itemEntityTranslucentCull(texture), RenderType.entityTranslucent(texture));
    }
    //?}

    //? if <1.21.11 {
    private static Optional<ResourceLocation> texture(RenderType type) {
        if (!(type instanceof RenderType.CompositeRenderType)) {
            return Optional.empty();
        }
        RenderType.CompositeState state = ((CompositeRenderTypeAccessor) type).htzdistance$getState();
        return ((EmptyTextureStateShardInvoker) ((CompositeStateAccessor) (Object) state).htzdistance$getTextureState())
                .htzdistance$cutoutTexture();
    }
    //?} else {
    /*/^* The main texture ("Sampler0") of the render setup. Found by field type, not by name. ^/
    private static Optional<ResourceLocation> texture(RenderType type) throws ReflectiveOperationException {
        RenderSetup setup = fieldOfType(type, RenderSetup.class);
        Map<?, ?> textures = setup == null ? null : fieldOfType(setup, Map.class);
        if (textures == null || textures.isEmpty()) {
            return Optional.empty();
        }
        Object binding = textures.get("Sampler0");
        if (binding == null) {
            binding = textures.values().iterator().next();
        }
        return Optional.ofNullable(fieldOfType(binding, ResourceLocation.class));
    }

    @SuppressWarnings("unchecked")
    private static <T> T fieldOfType(Object owner, Class<T> type) throws ReflectiveOperationException {
        for (Class<?> c = owner.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers()) && type.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return (T) field.get(owner);
                }
            }
        }
        return null;
    }
    *///?}
}
