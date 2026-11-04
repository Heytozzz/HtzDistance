package com.heytozzz.htzdistance.ghost;

import com.heytozzz.htzdistance.GhostStats;
import com.heytozzz.htzdistance.HtzDistanceClient;
import com.heytozzz.htzdistance.capture.GhostSnapshot;
import com.heytozzz.htzdistance.capture.RangeClassifier;
import com.heytozzz.htzdistance.capture.RangeLearner;
import com.heytozzz.htzdistance.capture.SnapshotFactory;
import com.heytozzz.htzdistance.config.ConfigManager;
import com.heytozzz.htzdistance.config.HtzConfig;
import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Owns the ghost store of the current server + dimension and reacts to entity removals, entity
 * additions, ticks and level changes. Client thread only.
 */
public final class GhostManager {
    private static final GhostManager INSTANCE = new GhostManager();

    /** Listeners notified when a store is closed (persistence) or opened. */
    public interface StoreListener {
        default void onStoreOpened(GhostStore store) {
        }

        default void onStoreClosing(GhostStore store) {
        }
    }

    private final List<StoreListener> listeners = new ArrayList<>();

    @Nullable
    private GhostStore store;
    @Nullable
    private ClientLevel trackedLevel;

    private GhostManager() {
    }

    public static GhostManager get() {
        return INSTANCE;
    }

    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientEntityEvents.ENTITY_LOAD.register(this::onEntityLoad);
    }

    public void addListener(StoreListener listener) {
        listeners.add(listener);
    }

    /** Current store, or {@code null} when not in a world. */
    @Nullable
    public GhostStore store() {
        return store;
    }

    // ------------------------------------------------------------------ capture

    /** Called from the packet mixin before the client removes the given entities. */
    public void onRemoveEntities(IntList entityIds) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        HtzConfig config = ConfigManager.get();
        if (level == null || player == null || !config.enabled) {
            return;
        }
        GhostStore current = ensureStore(minecraft, level);
        if (current == null) {
            return;
        }

        long startNanos = System.nanoTime();
        int captured = 0;
        boolean added = false;
        for (int i = 0; i < entityIds.size(); i++) {
            Entity entity = level.getEntity(entityIds.getInt(i));
            if (entity == null) {
                continue;
            }
            GhostKind kind = SnapshotFactory.kindOf(entity);
            if (kind == null) {
                continue;
            }
            double distance = RangeClassifier.horizontalDistance(player.getX(), player.getZ(), entity.getX(), entity.getZ());
            if (config.learningMode) {
                RangeLearner.get().onRemoved(current.key().server(), kind, entity, distance);
            }
            if (!config.settingsFor(kind).enabled) {
                continue;
            }

            if (!RangeClassifier.leftTrackingRange(kind, distance)) {
                HtzDistanceClient.debug("Dropped {} at {} (distance {} < {}), treated as destroyed",
                        kind, entity.blockPosition(), fmt(distance), fmt(RangeClassifier.innerRange(kind)));
                continue;
            }

            GhostSnapshot snapshot = SnapshotFactory.capture(entity, kind, level);
            if (snapshot == null) {
                continue;
            }
            current.add(snapshot, config.reconcileRadius);
            added = true;
            captured++;
            HtzDistanceClient.debug("Captured {} at {} (distance {})", kind, entity.blockPosition(), fmt(distance));
        }

        if (captured > 0) {
            GhostStats.CAPTURE_NANOS.addAndGet(System.nanoTime() - startNanos);
            GhostStats.CAPTURES.addAndGet(captured);
        }
        if (added) {
            int dropped = current.enforceLimit(config.maxGhosts, player.getX(), player.getY(), player.getZ());
            if (dropped > 0) {
                HtzDistanceClient.debug("Ghost limit reached, dropped {} farthest ghosts", dropped);
            }
        }
    }

    // ------------------------------------------------------------------ reconciliation

    private void onEntityLoad(Entity entity, ClientLevel level) {
        GhostKind kind = SnapshotFactory.kindOf(entity);
        if (kind == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        GhostStore current = ensureStore(minecraft, level);
        if (current == null) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player != null && ConfigManager.get().learningMode) {
            double distance = RangeClassifier.horizontalDistance(player.getX(), player.getZ(), entity.getX(), entity.getZ());
            RangeLearner.get().onAdded(current.key().server(), kind, entity, distance);
        }
        int removed = current.removeNear(kind, entity.getX(), entity.getY(), entity.getZ(), ConfigManager.get().reconcileRadius);
        if (removed > 0) {
            HtzDistanceClient.debug("Real {} is back at {}, removed {} ghost(s)", kind, entity.blockPosition(), removed);
        }
    }

    private void tick(Minecraft minecraft) {
        GhostStore current = ensureStore(minecraft, minecraft.level);
        LocalPlayer player = minecraft.player;
        RangeLearner.get().tickPlayer(player);
        if (current == null || player == null || current.size() == 0) {
            return;
        }

        HtzConfig config = ConfigManager.get();
        if (!config.enabled) {
            current.clear();
            return;
        }

        long now = Util.getMillis();
        long timeoutMs = (long) (config.inRangeTimeoutSeconds * 1000.0);
        double px = player.getX();
        double pz = player.getZ();

        Iterator<Ghost> it = current.iterator();
        boolean changed = false;
        while (it.hasNext()) {
            Ghost ghost = it.next();
            GhostKind kind = ghost.kind();
            if (!config.settingsFor(kind).enabled) {
                it.remove();
                changed = true;
                continue;
            }

            GhostSnapshot snapshot = ghost.snapshot();
            double distance = RangeClassifier.horizontalDistance(px, pz, snapshot.x(), snapshot.z());
            if (distance >= RangeClassifier.innerRange(kind)) {
                ghost.setInRangeSince(-1L);
                continue;
            }

            // The player is clearly inside the real tracking range: the server should have sent
            // the entity again. If it does not within the timeout, it was destroyed meanwhile.
            if (ghost.inRangeSince() < 0L) {
                ghost.setInRangeSince(now);
            } else if (now - ghost.inRangeSince() >= timeoutMs) {
                it.remove();
                changed = true;
                HtzDistanceClient.debug("Removed {} ghost at ({}, {}, {}): not resent while in range",
                        kind, fmt(snapshot.x()), fmt(snapshot.y()), fmt(snapshot.z()));
            }
        }
        if (changed) {
            current.markChanged();
        }
    }

    // ------------------------------------------------------------------ level lifecycle

    /**
     * Makes sure the store matches the given level: a different server or dimension closes the
     * old store and opens a new one; a new level for the same key (respawn) keeps the ghosts but
     * rebuilds their render entities against the new level.
     */
    @Nullable
    private GhostStore ensureStore(Minecraft minecraft, @Nullable ClientLevel level) {
        if (level == trackedLevel) {
            return store;
        }
        trackedLevel = level;

        WorldKey newKey = WorldKey.of(minecraft, level);
        if (store != null && store.key().equals(newKey)) {
            store.invalidateRenderEntities();
            return store;
        }

        closeStore();
        if (newKey != null) {
            store = new GhostStore(newKey);
            for (StoreListener listener : listeners) {
                listener.onStoreOpened(store);
            }
            HtzDistanceClient.debug("Opened ghost store for {}", newKey);
        }
        return store;
    }

    /** Notifies listeners and drops the current store. */
    public void closeStore() {
        if (store == null) {
            return;
        }
        GhostStore closing = store;
        store = null;
        for (StoreListener listener : listeners) {
            try {
                listener.onStoreClosing(closing);
            } catch (RuntimeException e) {
                HtzDistanceClient.LOGGER.error("Ghost store listener failed for {}", closing.key(), e);
            }
        }
        HtzDistanceClient.debug("Closed ghost store for {} ({} ghosts)", closing.key(), closing.size());
        closing.clear();
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
