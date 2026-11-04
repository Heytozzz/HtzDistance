package com.heytozzz.htzdistance.culling;

import com.heytozzz.htzdistance.GhostStats;
import com.heytozzz.htzdistance.HtzDistanceClient;
import com.heytozzz.htzdistance.capture.GhostSnapshot;
import com.heytozzz.htzdistance.config.ConfigManager;
import com.heytozzz.htzdistance.ghost.Ghost;
import com.heytozzz.htzdistance.ghost.GhostKind;
import com.heytozzz.htzdistance.ghost.GhostStore;
import com.logisticscraft.occlusionculling.OcclusionCullingInstance;
import com.logisticscraft.occlusionculling.util.Vec3d;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Asynchronous line-of-sight checks for ghosts.
 *
 * <p>The render thread publishes the camera position, the level and a copy of the ghost list;
 * a single background thread ray-traces each ghost's bounding box with the LogisticsCraft
 * OcclusionCulling library and writes the result into {@link Ghost#setVisible}. The render
 * thread never waits for it: it just reads the latest flags.
 */
public final class CullingWorker {
    private static final CullingWorker INSTANCE = new CullingWorker();
    /** Minimum time between two passes. */
    private static final long PASS_INTERVAL_MS = 20L;
    /** Max ray distance in blocks; ghosts farther away are not rendered anyway. */
    private static final int MAX_DISTANCE = 256;

    private final LevelDataProvider provider = new LevelDataProvider();
    private final OcclusionCullingInstance culling = new OcclusionCullingInstance(MAX_DISTANCE, provider);
    private final Vec3d aabbMin = new Vec3d(0, 0, 0);
    private final Vec3d aabbMax = new Vec3d(0, 0, 0);
    private final Vec3d viewer = new Vec3d(0, 0, 0);

    private volatile Vec3 cameraPos;
    private volatile ClientLevel level;
    private volatile List<Ghost> ghosts = List.of();
    private int publishedRevision = -1;
    private GhostStore publishedStore;

    private volatile boolean running;
    private Thread thread;

    private CullingWorker() {
    }

    public static CullingWorker get() {
        return INSTANCE;
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(this::run, "HtzDistance Culling");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    /** Called on the render thread once per frame with the current state. */
    public void publish(ClientLevel level, Vec3 cameraPos, GhostStore store) {
        this.level = level;
        this.cameraPos = cameraPos;
        if (store != publishedStore || store.revision() != publishedRevision) {
            publishedStore = store;
            publishedRevision = store.revision();
            ghosts = List.copyOf(store.view());
        }
    }

    /** Called when the world is left. */
    public void clear() {
        level = null;
        cameraPos = null;
        ghosts = List.of();
        publishedStore = null;
        publishedRevision = -1;
    }

    private void run() {
        while (running) {
            long start = System.nanoTime();
            try {
                pass();
            } catch (Throwable t) {
                // Chunk reads off-thread can fail transiently; never kill the worker.
                HtzDistanceClient.debug("Culling pass failed: {}", t.toString());
            }
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            try {
                Thread.sleep(Math.max(1L, PASS_INTERVAL_MS - elapsedMs));
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void pass() {
        List<Ghost> current = ghosts;
        ClientLevel currentLevel = level;
        Vec3 camera = cameraPos;
        if (current.isEmpty() || currentLevel == null || camera == null) {
            return;
        }
        if (!ConfigManager.get().occlusionCulling) {
            for (Ghost ghost : current) {
                ghost.setVisible(true);
            }
            return;
        }

        long startNanos = System.nanoTime();
        provider.setLevel(currentLevel);
        culling.resetCache();
        viewer.set(camera.x, camera.y, camera.z);
        for (Ghost ghost : current) {
            setBounds(ghost.snapshot());
            ghost.setVisible(culling.isAABBVisible(aabbMin, aabbMax, viewer));
        }
        provider.cleanup();
        GhostStats.CULLING_NANOS.addAndGet(System.nanoTime() - startNanos);
        GhostStats.CULLING_PASSES.incrementAndGet();
    }

    /** Conservative bounding box per kind (slightly larger than the real one). */
    private void setBounds(GhostSnapshot snapshot) {
        double x = snapshot.x();
        double y = snapshot.y();
        double z = snapshot.z();
        if (snapshot.kind() == GhostKind.ARMOR_STAND) {
            aabbMin.set(x - 0.5, y, z - 0.5);
            aabbMax.set(x + 0.5, y + 2.0, z + 0.5);
        } else {
            aabbMin.set(x - 0.5, y - 0.5, z - 0.5);
            aabbMax.set(x + 0.5, y + 0.5, z + 0.5);
        }
    }
}
