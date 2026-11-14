package com.heytozzz.htzdistance.render;

//? if >=26.3 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Vector3f;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.resources.model.geometry.ItemQuads;

/^*
 * Draws item and block model submits of holograms as custom geometry built from their quads, so
 * every vertex goes through {@link TintingVertexConsumer}. Mirrors the vanilla feature renderers
 * (light, overlay, tint layers); the enchantment glint is left out.
 ^/
public final class HologramQuads {
    private static final Direction[] SIDES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null};

    private HologramQuads() {
    }

    static boolean isBlockModel(Class<?>[] params) {
        return HologramSubmits.indexOf(params, List.class) >= 0 && HologramSubmits.indexOf(params, int[].class) >= 0;
    }

    /^* (pose stack, display context, light, overlay, outline, tint layers, quads, foil). ^/
    @SuppressWarnings("unchecked")
    static void submitItem(OrderedSubmitNodeCollector target, Object[] args, GhostStyleColor style) {
        PoseStack poseStack = (PoseStack) args[0];
        int light = (Integer) args[2];
        int overlay = (Integer) args[3];
        int[] tints = (int[]) args[5];
        List<BakedQuad> quads = ((ItemQuads) args[6]).all();
        // Items carry their render type per quad.
        Map<RenderType, List<BakedQuad>> groups = new LinkedHashMap<>();
        for (BakedQuad quad : quads) {
            groups.computeIfAbsent(quad.materialInfo().itemRenderType(), type -> new ArrayList<>()).add(quad);
        }
        groups.forEach((type, group) -> target.submitCustomGeometry(poseStack, HologramSubmits.styled(type, style),
                (pose, consumer) -> {
                    QuadInstance instance = quadInstance(light, overlay);
                    VertexConsumer out = new TintingVertexConsumer(consumer, style);
                    for (BakedQuad quad : group) {
                        put(out, pose, quad, instance, tints);
                    }
                }));
    }

    /^* (pose stack, render type, model parts, tint layers, light, overlay, outline). ^/
    @SuppressWarnings("unchecked")
    static void submitBlockModel(OrderedSubmitNodeCollector target, Object[] args, GhostStyleColor style) {
        RenderType type = (RenderType) args[1];
        submitParts(target, (PoseStack) args[0], layer -> type, (List<BlockStateModelPart>) args[2],
                (int[]) args[3], (Integer) args[4], (Integer) args[5], style);
    }

    static boolean isFabricBlockModel(Class<?>[] params) {
        return params.length == 9 && params[1] == Function.class && params[3] == List.class && params[5] == int[].class;
    }

    /^*
     * Fabric API's variant (pose stack, render type per layer, flag, model parts, mesh, tint
     * layers, light, overlay, outline). Fabric's renderer turns vanilla models such as the item
     * frame into the mesh, so both the parts and the mesh quads are drawn.
     ^/
    @SuppressWarnings("unchecked")
    static void submitFabricBlockModel(OrderedSubmitNodeCollector target, Object[] args, GhostStyleColor style) {
        PoseStack poseStack = (PoseStack) args[0];
        Function<ChunkSectionLayer, RenderType> types = (Function<ChunkSectionLayer, RenderType>) args[1];
        int[] tints = (int[]) args[5];
        int light = (Integer) args[6];
        int overlay = (Integer) args[7];
        submitParts(target, poseStack, types, (List<BlockStateModelPart>) args[3], tints, light, overlay, style);
        if (args[4] instanceof Mesh mesh && mesh.size() > 0) {
            // The mesh may be reused once this call returns: copy its quads now.
            Map<RenderType, List<float[]>> groups = new LinkedHashMap<>();
            mesh.forEach(quad -> groups.computeIfAbsent(types.apply(quad.chunkLayer()), type -> new ArrayList<>())
                    .add(copy(quad, tints, light)));
            groups.forEach((type, group) -> target.submitCustomGeometry(poseStack, HologramSubmits.styled(type, style),
                    (pose, consumer) -> {
                        VertexConsumer out = new TintingVertexConsumer(consumer, style);
                        for (float[] quad : group) {
                            putMeshQuad(out, pose, quad, overlay);
                        }
                    }));
        }
    }

    private static final int VERTEX_FLOATS = 9;

    /^* One mesh quad as 4 x (x, y, z, u, v, color, light, normal x, normal z) + normal y. ^/
    private static float[] copy(QuadView quad, int[] tints, int light) {
        float[] data = new float[4 * VERTEX_FLOATS + 4];
        int tint = quad.tintIndex() >= 0 ? layer(tints, quad.tintIndex()) : -1;
        for (int i = 0; i < 4; i++) {
            int o = i * VERTEX_FLOATS;
            data[o] = quad.x(i);
            data[o + 1] = quad.y(i);
            data[o + 2] = quad.z(i);
            data[o + 3] = quad.u(i);
            data[o + 4] = quad.v(i);
            data[o + 5] = Float.intBitsToFloat(ARGB.multiply(quad.color(i), tint));
            data[o + 6] = Float.intBitsToFloat(quad.emissive() ? LightCoordsUtil.FULL_BRIGHT : LightCoordsUtil.max(light, quad.lightmap(i)));
            boolean own = quad.hasNormal(i);
            data[o + 7] = own ? quad.normalX(i) : quad.faceNormal().x();
            data[o + 8] = own ? quad.normalZ(i) : quad.faceNormal().z();
            data[4 * VERTEX_FLOATS + i] = own ? quad.normalY(i) : quad.faceNormal().y();
        }
        return data;
    }

    private static void putMeshQuad(VertexConsumer out, PoseStack.Pose pose, float[] quad, int overlay) {
        Vector3f position = new Vector3f();
        Vector3f normal = new Vector3f();
        for (int i = 0; i < 4; i++) {
            int o = i * VERTEX_FLOATS;
            pose.pose().transformPosition(quad[o], quad[o + 1], quad[o + 2], position);
            pose.transformNormal(quad[o + 7], quad[4 * VERTEX_FLOATS + i], quad[o + 8], normal);
            out.addVertex(position.x(), position.y(), position.z(), Float.floatToRawIntBits(quad[o + 5]), quad[o + 3], quad[o + 4],
                    overlay, Float.floatToRawIntBits(quad[o + 6]), normal.x(), normal.y(), normal.z());
        }
    }

    private static void submitParts(OrderedSubmitNodeCollector target, PoseStack poseStack, Function<ChunkSectionLayer, RenderType> types,
                                    List<BlockStateModelPart> parts, int[] tints, int light, int overlay, GhostStyleColor style) {
        Map<RenderType, List<BakedQuad>> groups = new LinkedHashMap<>();
        for (BlockStateModelPart part : parts) {
            for (Direction side : SIDES) {
                for (BakedQuad quad : part.getQuads(side)) {
                    groups.computeIfAbsent(types.apply(quad.materialInfo().layer()), type -> new ArrayList<>()).add(quad);
                }
            }
        }
        groups.forEach((type, group) -> target.submitCustomGeometry(poseStack, HologramSubmits.styled(type, style),
                (pose, consumer) -> {
                    QuadInstance instance = quadInstance(light, overlay);
                    VertexConsumer out = new TintingVertexConsumer(consumer, style);
                    for (BakedQuad quad : group) {
                        put(out, pose, quad, instance, tints);
                    }
                }));
    }

    private static QuadInstance quadInstance(int light, int overlay) {
        QuadInstance instance = new QuadInstance();
        instance.setLightCoords(light);
        instance.setOverlayCoords(overlay);
        return instance;
    }

    private static void put(VertexConsumer out, PoseStack.Pose pose, BakedQuad quad, QuadInstance instance, int[] tints) {
        BakedQuad.MaterialInfo material = quad.materialInfo();
        instance.setColor(material.isTinted() ? layer(tints, material.tintIndex()) : -1);
        out.putBakedQuad(pose, quad, instance);
    }

    private static int layer(int[] tints, int index) {
        return index >= 0 && index < tints.length ? tints[index] : -1;
    }
}
*///?} else if >=26.1 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Vector3f;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/^*
 * Draws item and block model submits of holograms as custom geometry built from their quads, so
 * every vertex goes through {@link TintingVertexConsumer}. Mirrors the vanilla feature renderers
 * (light, overlay, tint layers); the enchantment glint is left out.
 ^/
public final class HologramQuads {
    private static final Direction[] SIDES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null};

    private HologramQuads() {
    }

    static boolean isBlockModel(Class<?>[] params) {
        return HologramSubmits.indexOf(params, List.class) >= 0 && HologramSubmits.indexOf(params, int[].class) >= 0;
    }

    /^* (pose stack, display context, light, overlay, outline, tint layers, quads, foil). ^/
    @SuppressWarnings("unchecked")
    static void submitItem(OrderedSubmitNodeCollector target, Object[] args, GhostStyleColor style) {
        PoseStack poseStack = (PoseStack) args[0];
        int light = (Integer) args[2];
        int overlay = (Integer) args[3];
        int[] tints = (int[]) args[5];
        List<BakedQuad> quads = (List<BakedQuad>) args[6];
        // Items carry their render type per quad.
        Map<RenderType, List<BakedQuad>> groups = new LinkedHashMap<>();
        for (BakedQuad quad : quads) {
            groups.computeIfAbsent(quad.materialInfo().itemRenderType(), type -> new ArrayList<>()).add(quad);
        }
        groups.forEach((type, group) -> target.submitCustomGeometry(poseStack, HologramSubmits.styled(type, style),
                (pose, consumer) -> {
                    QuadInstance instance = quadInstance(light, overlay);
                    VertexConsumer out = new TintingVertexConsumer(consumer, style);
                    for (BakedQuad quad : group) {
                        put(out, pose, quad, instance, tints);
                    }
                }));
    }

    /^* (pose stack, render type, model parts, tint layers, light, overlay, outline). ^/
    @SuppressWarnings("unchecked")
    static void submitBlockModel(OrderedSubmitNodeCollector target, Object[] args, GhostStyleColor style) {
        RenderType type = (RenderType) args[1];
        submitParts(target, (PoseStack) args[0], layer -> type, (List<BlockStateModelPart>) args[2],
                (int[]) args[3], (Integer) args[4], (Integer) args[5], style);
    }

    static boolean isFabricBlockModel(Class<?>[] params) {
        return params.length == 9 && params[1] == Function.class && params[3] == List.class && params[5] == int[].class;
    }

    /^*
     * Fabric API's variant (pose stack, render type per layer, flag, model parts, mesh, tint
     * layers, light, overlay, outline). Fabric's renderer turns vanilla models such as the item
     * frame into the mesh, so both the parts and the mesh quads are drawn.
     ^/
    @SuppressWarnings("unchecked")
    static void submitFabricBlockModel(OrderedSubmitNodeCollector target, Object[] args, GhostStyleColor style) {
        PoseStack poseStack = (PoseStack) args[0];
        Function<ChunkSectionLayer, RenderType> types = (Function<ChunkSectionLayer, RenderType>) args[1];
        int[] tints = (int[]) args[5];
        int light = (Integer) args[6];
        int overlay = (Integer) args[7];
        submitParts(target, poseStack, types, (List<BlockStateModelPart>) args[3], tints, light, overlay, style);
        if (args[4] instanceof Mesh mesh && mesh.size() > 0) {
            // The mesh may be reused once this call returns: copy its quads now.
            Map<RenderType, List<float[]>> groups = new LinkedHashMap<>();
            mesh.forEach(quad -> groups.computeIfAbsent(types.apply(quad.chunkLayer()), type -> new ArrayList<>())
                    .add(copy(quad, tints, light)));
            groups.forEach((type, group) -> target.submitCustomGeometry(poseStack, HologramSubmits.styled(type, style),
                    (pose, consumer) -> {
                        VertexConsumer out = new TintingVertexConsumer(consumer, style);
                        for (float[] quad : group) {
                            putMeshQuad(out, pose, quad, overlay);
                        }
                    }));
        }
    }

    private static final int VERTEX_FLOATS = 9;

    /^* One mesh quad as 4 x (x, y, z, u, v, color, light, normal x, normal z) + normal y. ^/
    private static float[] copy(QuadView quad, int[] tints, int light) {
        float[] data = new float[4 * VERTEX_FLOATS + 4];
        int tint = quad.tintIndex() >= 0 ? layer(tints, quad.tintIndex()) : -1;
        for (int i = 0; i < 4; i++) {
            int o = i * VERTEX_FLOATS;
            data[o] = quad.x(i);
            data[o + 1] = quad.y(i);
            data[o + 2] = quad.z(i);
            data[o + 3] = quad.u(i);
            data[o + 4] = quad.v(i);
            data[o + 5] = Float.intBitsToFloat(ARGB.multiply(quad.color(i), tint));
            data[o + 6] = Float.intBitsToFloat(quad.emissive() ? LightCoordsUtil.FULL_BRIGHT : LightCoordsUtil.max(light, quad.lightmap(i)));
            boolean own = quad.hasNormal(i);
            data[o + 7] = own ? quad.normalX(i) : quad.faceNormal().x();
            data[o + 8] = own ? quad.normalZ(i) : quad.faceNormal().z();
            data[4 * VERTEX_FLOATS + i] = own ? quad.normalY(i) : quad.faceNormal().y();
        }
        return data;
    }

    private static void putMeshQuad(VertexConsumer out, PoseStack.Pose pose, float[] quad, int overlay) {
        Vector3f position = new Vector3f();
        Vector3f normal = new Vector3f();
        for (int i = 0; i < 4; i++) {
            int o = i * VERTEX_FLOATS;
            pose.pose().transformPosition(quad[o], quad[o + 1], quad[o + 2], position);
            pose.transformNormal(quad[o + 7], quad[4 * VERTEX_FLOATS + i], quad[o + 8], normal);
            out.addVertex(position.x(), position.y(), position.z(), Float.floatToRawIntBits(quad[o + 5]), quad[o + 3], quad[o + 4],
                    overlay, Float.floatToRawIntBits(quad[o + 6]), normal.x(), normal.y(), normal.z());
        }
    }

    private static void submitParts(OrderedSubmitNodeCollector target, PoseStack poseStack, Function<ChunkSectionLayer, RenderType> types,
                                    List<BlockStateModelPart> parts, int[] tints, int light, int overlay, GhostStyleColor style) {
        Map<RenderType, List<BakedQuad>> groups = new LinkedHashMap<>();
        for (BlockStateModelPart part : parts) {
            for (Direction side : SIDES) {
                for (BakedQuad quad : part.getQuads(side)) {
                    groups.computeIfAbsent(types.apply(quad.materialInfo().layer()), type -> new ArrayList<>()).add(quad);
                }
            }
        }
        groups.forEach((type, group) -> target.submitCustomGeometry(poseStack, HologramSubmits.styled(type, style),
                (pose, consumer) -> {
                    QuadInstance instance = quadInstance(light, overlay);
                    VertexConsumer out = new TintingVertexConsumer(consumer, style);
                    for (BakedQuad quad : group) {
                        put(out, pose, quad, instance, tints);
                    }
                }));
    }

    private static QuadInstance quadInstance(int light, int overlay) {
        QuadInstance instance = new QuadInstance();
        instance.setLightCoords(light);
        instance.setOverlayCoords(overlay);
        return instance;
    }

    private static void put(VertexConsumer out, PoseStack.Pose pose, BakedQuad quad, QuadInstance instance, int[] tints) {
        BakedQuad.MaterialInfo material = quad.materialInfo();
        instance.setColor(material.isTinted() ? layer(tints, material.tintIndex()) : -1);
        out.putBakedQuad(pose, quad, instance);
    }

    private static int layer(int[] tints, int index) {
        return index >= 0 && index < tints.length ? tints[index] : -1;
    }
}
*///?} else if >=1.21.11 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.List;

/^*
 * Draws item and block model submits of holograms as custom geometry built from their quads, so
 * every vertex goes through {@link TintingVertexConsumer}. Mirrors the vanilla feature renderers
 * (light, overlay, tint layers); the enchantment glint is left out.
 ^/
public final class HologramQuads {
    private HologramQuads() {
    }

    static boolean isBlockModel(Class<?>[] params) {
        return HologramSubmits.indexOf(params, BlockStateModel.class) >= 0;
    }

    /^* (pose stack, display context, light, overlay, outline, tint layers, quads, render type, foil). ^/
    @SuppressWarnings("unchecked")
    static void submitItem(OrderedSubmitNodeCollector target, Object[] args, GhostStyleColor style) {
        PoseStack poseStack = (PoseStack) args[0];
        int light = (Integer) args[2];
        int overlay = (Integer) args[3];
        int[] tints = (int[]) args[5];
        List<BakedQuad> quads = (List<BakedQuad>) args[6];
        RenderType type = (RenderType) args[7];
        target.submitCustomGeometry(poseStack, HologramSubmits.styled(type, style), (pose, consumer) -> {
            VertexConsumer out = new TintingVertexConsumer(consumer, style);
            for (BakedQuad quad : quads) {
                int color = quad.isTinted() ? layer(tints, quad.tintIndex()) : -1;
                out.putBulkData(pose, quad,
                        ((color >> 16) & 0xFF) / 255.0f, ((color >> 8) & 0xFF) / 255.0f, (color & 0xFF) / 255.0f,
                        (color >>> 24) / 255.0f, light, overlay);
            }
        });
    }

    /^* Fabric API's mesh-aware variant is passed on unstyled on these versions. ^/
    static boolean isFabricBlockModel(Class<?>[] params) {
        return false;
    }

    static void submitFabricBlockModel(OrderedSubmitNodeCollector target, Object[] args, GhostStyleColor style) {
        throw new UnsupportedOperationException();
    }

    /^* (pose stack, render type, model, red, green, blue, light, overlay, outline). ^/
    static void submitBlockModel(OrderedSubmitNodeCollector target, Object[] args, GhostStyleColor style) {
        PoseStack poseStack = (PoseStack) args[0];
        RenderType type = (RenderType) args[1];
        BlockStateModel model = (BlockStateModel) args[2];
        float red = (Float) args[3];
        float green = (Float) args[4];
        float blue = (Float) args[5];
        int light = (Integer) args[6];
        int overlay = (Integer) args[7];
        target.submitCustomGeometry(poseStack, HologramSubmits.styled(type, style), (pose, consumer) ->
                ModelBlockRenderer.renderModel(pose, new TintingVertexConsumer(consumer, style), model, red, green, blue, light, overlay));
    }

    private static int layer(int[] tints, int index) {
        return index >= 0 && index < tints.length ? tints[index] : -1;
    }
}
*///?} else if >=1.21.10 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockStateModel;

import java.util.List;

/^*
 * Draws item and block model submits of holograms as custom geometry built from their quads, so
 * every vertex goes through {@link TintingVertexConsumer}. Mirrors the vanilla feature renderers
 * (light, overlay, tint layers); the enchantment glint is left out.
 ^/
public final class HologramQuads {
    private HologramQuads() {
    }

    static boolean isBlockModel(Class<?>[] params) {
        return HologramSubmits.indexOf(params, BlockStateModel.class) >= 0;
    }

    /^* (pose stack, display context, light, overlay, outline, tint layers, quads, render type, foil). ^/
    @SuppressWarnings("unchecked")
    static void submitItem(OrderedSubmitNodeCollector target, Object[] args, GhostStyleColor style) {
        PoseStack poseStack = (PoseStack) args[0];
        int light = (Integer) args[2];
        int overlay = (Integer) args[3];
        int[] tints = (int[]) args[5];
        List<BakedQuad> quads = (List<BakedQuad>) args[6];
        RenderType type = (RenderType) args[7];
        target.submitCustomGeometry(poseStack, HologramSubmits.styled(type, style), (pose, consumer) -> {
            VertexConsumer out = new TintingVertexConsumer(consumer, style);
            for (BakedQuad quad : quads) {
                int color = quad.isTinted() ? layer(tints, quad.tintIndex()) : -1;
                out.putBulkData(pose, quad,
                        ((color >> 16) & 0xFF) / 255.0f, ((color >> 8) & 0xFF) / 255.0f, (color & 0xFF) / 255.0f,
                        (color >>> 24) / 255.0f, light, overlay);
            }
        });
    }

    /^* Fabric API's mesh-aware variant is passed on unstyled on these versions. ^/
    static boolean isFabricBlockModel(Class<?>[] params) {
        return false;
    }

    static void submitFabricBlockModel(OrderedSubmitNodeCollector target, Object[] args, GhostStyleColor style) {
        throw new UnsupportedOperationException();
    }

    /^* (pose stack, render type, model, red, green, blue, light, overlay, outline). ^/
    static void submitBlockModel(OrderedSubmitNodeCollector target, Object[] args, GhostStyleColor style) {
        PoseStack poseStack = (PoseStack) args[0];
        RenderType type = (RenderType) args[1];
        BlockStateModel model = (BlockStateModel) args[2];
        float red = (Float) args[3];
        float green = (Float) args[4];
        float blue = (Float) args[5];
        int light = (Integer) args[6];
        int overlay = (Integer) args[7];
        target.submitCustomGeometry(poseStack, HologramSubmits.styled(type, style), (pose, consumer) ->
                ModelBlockRenderer.renderModel(pose, new TintingVertexConsumer(consumer, style), model, red, green, blue, light, overlay));
    }

    private static int layer(int[] tints, int index) {
        return index >= 0 && index < tints.length ? tints[index] : -1;
    }
}
*///?}
