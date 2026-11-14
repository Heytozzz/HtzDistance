package com.heytozzz.htzdistance.render;

//? if >=1.21.11 {
/*import net.minecraft.client.renderer.rendertype.RenderType;
*///?} else if >=1.21.10 {
/*import net.minecraft.client.renderer.RenderType;
*///?}
//? if >=1.21.10 {
/*import com.heytozzz.htzdistance.HtzDistanceClient;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.item.ItemDisplayContext;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/^*
 * Applies the hologram style (tint, translucency) to what hologram render states submit, on
 * Minecraft 1.21.10+. Render states cannot be tinted directly, so while a hologram state is
 * submitted the collector is replaced by a proxy that rewrites the submits:
 * <ul>
 *     <li>models and model parts: the tint argument is multiplied by the style color, and the
 *     render type is swapped for a translucent one when needed;</li>
 *     <li>custom geometry (paintings, maps): the vertices are recolored;</li>
 *     <li>items and block models (the frame itself): drawn as custom geometry from their quads
 *     (see {@link HologramQuads}), because vanilla only tints tinted quads.</li>
 * </ul>
 * Methods are told apart by parameter types, not names, so the proxy works on every supported
 * version, obfuscated or not. Nothing is wrapped with the normal style. Render thread only.
 ^/
public final class HologramSubmits {
    private static final Set<Object> STATES = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<Method, Kind> KINDS = new ConcurrentHashMap<>();
    @Nullable
    private static GhostStyleColor style;
    private static boolean failed;

    private enum Kind { ORDER, DEFAULT, MODEL, CUSTOM, ITEM, BLOCK_MODEL, FABRIC_BLOCK_MODEL, PASS }

    private HologramSubmits() {
    }

    /^* Starts a frame: forgets last frame's states and remembers the style to apply. ^/
    static void beginFrame(GhostStyleColor frameStyle) {
        STATES.clear();
        style = frameStyle == GhostStyleColor.NONE || failed ? null : frameStyle;
    }

    /^* Whether styling failed once and was turned off (for tests). ^/
    public static boolean styleFailed() {
        return failed;
    }

    /^* Marks a render state as a hologram for this frame. ^/
    static void add(Object state) {
        if (style != null) {
            STATES.add(state);
        }
    }

    /^* The collector to submit {@code state} with: styled for holograms, unchanged otherwise. ^/
    public static SubmitNodeCollector wrap(SubmitNodeCollector collector, Object state) {
        GhostStyleColor current = style;
        if (current == null || !STATES.contains(state) || isWrapped(collector)) {
            return collector;
        }
        return (SubmitNodeCollector) Proxy.newProxyInstance(SubmitNodeCollector.class.getClassLoader(),
                new Class<?>[]{SubmitNodeCollector.class}, new Handler(collector, current));
    }

    private static boolean isWrapped(Object collector) {
        return Proxy.isProxyClass(collector.getClass()) && Proxy.getInvocationHandler(collector) instanceof Handler;
    }

    private static Kind classify(Method method, Class<?> targetClass) {
        Class<?>[] params = method.getParameterTypes();
        if (method.isDefault()) {
            if (!overridden(method, targetClass)) {
                // Default methods end up in the abstract ones, which come through the proxy again.
                return Kind.DEFAULT;
            }
            // Implemented by the collector itself (Fabric API adds mesh-aware variants): the
            // interface default would take a different path, so handle the known ones here
            // and pass the rest on unstyled.
            if (indexOf(params, ItemDisplayContext.class) >= 0) {
                return Kind.ITEM;
            }
            return HologramQuads.isFabricBlockModel(params) ? Kind.FABRIC_BLOCK_MODEL : Kind.PASS;
        }
        if (params.length == 1 && params[0] == int.class && OrderedSubmitNodeCollector.class.isAssignableFrom(method.getReturnType())) {
            return Kind.ORDER;
        }
        if (indexOf(params, ItemDisplayContext.class) >= 0) {
            return Kind.ITEM;
        }
        if (indexOf(params, RenderType.class) < 0) {
            return Kind.PASS;
        }
        if (indexOf(params, SubmitNodeCollector.CustomGeometryRenderer.class) >= 0) {
            return Kind.CUSTOM;
        }
        if (params.length > 0 && (Model.class.isAssignableFrom(params[0]) || params[0] == ModelPart.class)) {
            return Kind.MODEL;
        }
        return HologramQuads.isBlockModel(params) ? Kind.BLOCK_MODEL : Kind.PASS;
    }

    private static boolean overridden(Method method, Class<?> targetClass) {
        try {
            return targetClass.getMethod(method.getName(), method.getParameterTypes()).getDeclaringClass() != method.getDeclaringClass();
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    static int indexOf(Class<?>[] params, Class<?> type) {
        for (int i = 0; i < params.length; i++) {
            if (params[i] == type) {
                return i;
            }
        }
        return -1;
    }

    static RenderType styled(RenderType type, GhostStyleColor style) {
        return style.translucent() ? GhostRenderTypes.translucentFor(type) : type;
    }

    private record Handler(Object target, GhostStyleColor style) implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> "HologramSubmits[" + target + "]";
                };
            }
            Kind kind = KINDS.computeIfAbsent(method, m -> classify(m, target.getClass()));
            if (kind == Kind.ORDER) {
                return Proxy.newProxyInstance(SubmitNodeCollector.class.getClassLoader(),
                        new Class<?>[]{OrderedSubmitNodeCollector.class}, new Handler(forward(method, args), style));
            }
            if (kind == Kind.DEFAULT) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            if (!failed) {
                try {
                    switch (kind) {
                        case MODEL -> styleModel(method.getParameterTypes(), args);
                        case CUSTOM -> styleCustom(method.getParameterTypes(), args);
                        case ITEM -> {
                            HologramQuads.submitItem((OrderedSubmitNodeCollector) target, args, style);
                            return null;
                        }
                        case BLOCK_MODEL -> {
                            HologramQuads.submitBlockModel((OrderedSubmitNodeCollector) target, args, style);
                            return null;
                        }
                        case FABRIC_BLOCK_MODEL -> {
                            HologramQuads.submitFabricBlockModel((OrderedSubmitNodeCollector) target, args, style);
                            return null;
                        }
                        default -> {
                        }
                    }
                } catch (RuntimeException | LinkageError e) {
                    // Never break rendering because of the style: draw unstyled from now on.
                    failed = true;
                    HtzDistanceClient.LOGGER.warn("Could not apply the hologram style; holograms are drawn unstyled", e);
                }
            }
            return forward(method, args);
        }

        private Object forward(Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        /^* Models and model parts: the third int after the render type is the tint color. ^/
        private void styleModel(Class<?>[] params, Object[] args) {
            int renderType = indexOf(params, RenderType.class);
            args[renderType] = styled((RenderType) args[renderType], style);
            int ints = 0;
            for (int i = renderType + 1; i < params.length; i++) {
                if (params[i] == int.class && ++ints == 3) {
                    args[i] = style.apply((Integer) args[i]);
                    return;
                }
            }
        }

        /^* Custom geometry (paintings, maps): recolors the vertices. ^/
        private void styleCustom(Class<?>[] params, Object[] args) {
            int renderType = indexOf(params, RenderType.class);
            int rendererIndex = indexOf(params, SubmitNodeCollector.CustomGeometryRenderer.class);
            args[renderType] = styled((RenderType) args[renderType], style);
            SubmitNodeCollector.CustomGeometryRenderer renderer = (SubmitNodeCollector.CustomGeometryRenderer) args[rendererIndex];
            GhostStyleColor color = style;
            args[rendererIndex] = (SubmitNodeCollector.CustomGeometryRenderer) (pose, consumer) ->
                    renderer.render(pose, new TintingVertexConsumer(consumer, color));
        }
    }
}
*///?}
