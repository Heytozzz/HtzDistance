package com.heytozzz.htzdistance.ghost;

import com.heytozzz.htzdistance.capture.GhostSnapshot;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * One ghost: an immutable snapshot plus client-only runtime state. Only touched on the client
 * thread, except {@link #visible}, which the culling worker writes.
 */
public final class Ghost {
    private final GhostSnapshot snapshot;

    /** Client-only entity rebuilt from the snapshot for rendering. Never added to the level. */
    @Nullable
    private Entity renderEntity;

    /** Set when rebuilding the render entity failed, so it is not retried every frame. */
    private boolean renderEntityFailed;

    /** Time (ms) since the player has been inside the real tracking range, or -1. */
    private long inRangeSince = -1L;

    /** Written by the culling worker, read by the renderer. */
    private volatile boolean visible = true;

    /**
     * Render state extracted from {@link #renderEntity} and reused across frames (1.21.10+).
     * Typed as Object because the render state classes differ between versions.
     */
    @Nullable
    private Object cachedRenderState;
    /** {@link System#nanoTime()} after which {@link #cachedRenderState} must be refreshed. */
    private long cachedRenderStateExpiry;

    public Ghost(GhostSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public GhostSnapshot snapshot() {
        return snapshot;
    }

    public GhostKind kind() {
        return snapshot.kind();
    }

    @Nullable
    public Entity renderEntity() {
        return renderEntity;
    }

    public boolean renderEntityFailed() {
        return renderEntityFailed;
    }

    public void setRenderEntity(@Nullable Entity entity) {
        this.renderEntity = entity;
        this.renderEntityFailed = false;
        this.cachedRenderState = null;
    }

    public void markRenderEntityFailed() {
        this.renderEntity = null;
        this.renderEntityFailed = true;
        this.cachedRenderState = null;
    }

    /** Drops the render entity so it is rebuilt against the current level. */
    public void invalidateRenderEntity() {
        this.renderEntity = null;
        this.renderEntityFailed = false;
        this.cachedRenderState = null;
    }

    /** Cached render state if it has not expired at {@code nowNanos}, otherwise {@code null}. */
    @Nullable
    public Object cachedRenderState(long nowNanos) {
        return cachedRenderState != null && nowNanos - cachedRenderStateExpiry < 0 ? cachedRenderState : null;
    }

    public void setCachedRenderState(Object state, long expiryNanos) {
        this.cachedRenderState = state;
        this.cachedRenderStateExpiry = expiryNanos;
    }

    public long inRangeSince() {
        return inRangeSince;
    }

    public void setInRangeSince(long inRangeSince) {
        this.inRangeSince = inRangeSince;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }
}
