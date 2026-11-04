package com.heytozzz.htzdistance.render;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Multiplies every vertex color by the ghost style color. Only the primitive methods are
 * overridden, so the bulk default methods (models, quads) route through {@link #setColor}.
 */
public final class TintingVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final GhostStyleColor color;

    public TintingVertexConsumer(VertexConsumer delegate, GhostStyleColor color) {
        this.delegate = delegate;
        this.color = color;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        delegate.setColor(color.applyRed(red), color.applyGreen(green), color.applyBlue(blue), color.applyAlpha(alpha));
        return this;
    }

    //? if >=1.21.11 {
    /*@Override
    public VertexConsumer setColor(int argb) {
        int alpha = argb >>> 24;
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;
        return setColor(red, green, blue, alpha);
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        delegate.setLineWidth(width);
        return this;
    }
    *///?}

    //? if >=26.3 {
    /*@Override
    public VertexConsumer setUv3(float u, float v) {
        delegate.setUv3(u, v);
        return this;
    }
    *///?}

    @Override
    public VertexConsumer setUv(float u, float v) {
        delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        delegate.setNormal(x, y, z);
        return this;
    }
}
