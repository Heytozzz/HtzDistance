package com.heytozzz.htzdistance.persist;

import com.heytozzz.htzdistance.HtzDistanceClient;
import com.heytozzz.htzdistance.capture.GhostSnapshot;
import com.heytozzz.htzdistance.config.ConfigManager;
import com.heytozzz.htzdistance.config.HtzConfig;
import com.heytozzz.htzdistance.ghost.Ghost;
import com.heytozzz.htzdistance.ghost.GhostManager;
import com.heytozzz.htzdistance.ghost.GhostStore;
import com.heytozzz.htzdistance.ghost.WorldKey;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Optional on-disk storage of ghosts, one file per server and dimension, in the style of Bobby:
 * {@code <game dir>/htzdistance/<server>/<dimension>/ghosts.dat}.
 *
 * <p>Files are read when a store opens and written (on a background thread) when it closes,
 * every few minutes while it changes, and when the game stops. Files written by another
 * Minecraft data version are ignored, because entity NBT is not upgraded.
 */
public final class GhostPersistence implements GhostManager.StoreListener {
    private static final int FORMAT_VERSION = 1;
    private static final long AUTOSAVE_INTERVAL_MS = 5L * 60L * 1000L;

    private final ExecutorService io = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "HtzDistance IO");
        thread.setDaemon(true);
        return thread;
    });

    private int savedRevision = -1;
    private long lastSave;

    public void register() {
        GhostManager.get().addListener(this);
        ClientTickEvents.END_CLIENT_TICK.register(this::autosave);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            GhostManager.get().closeStore();
            io.shutdown();
            try {
                io.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    @Override
    public void onStoreOpened(GhostStore store) {
        savedRevision = store.revision();
        lastSave = Util.getMillis();
        if (!ConfigManager.get().persistence) {
            return;
        }
        Path file = fileFor(store.key());
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            int dataVersion = root.getIntOr("dataVersion", -1);
            if (root.getIntOr("format", -1) != FORMAT_VERSION || dataVersion != currentDataVersion()) {
                HtzDistanceClient.LOGGER.info("Ignoring ghosts saved for another game version in {}", file);
                return;
            }
            HtzConfig config = ConfigManager.get();
            int loaded = 0;
            for (Tag entry : root.getListOrEmpty("ghosts")) {
                if (entry instanceof CompoundTag tag) {
                    GhostSnapshot snapshot = GhostSerializer.read(tag);
                    if (snapshot != null && config.settingsFor(snapshot.kind()).enabled) {
                        store.add(snapshot, config.reconcileRadius);
                        loaded++;
                    }
                }
            }
            savedRevision = store.revision();
            HtzDistanceClient.LOGGER.info("Loaded {} ghosts for {}", loaded, store.key());
        } catch (IOException | RuntimeException e) {
            HtzDistanceClient.LOGGER.warn("Could not read ghosts from {}", file, e);
        }
    }

    @Override
    public void onStoreClosing(GhostStore store) {
        save(store);
    }

    private void autosave(Minecraft minecraft) {
        GhostStore store = GhostManager.get().store();
        if (store == null || store.revision() == savedRevision) {
            return;
        }
        if (Util.getMillis() - lastSave >= AUTOSAVE_INTERVAL_MS) {
            save(store);
        }
    }

    /** Serializes on the calling (client) thread, writes on the IO thread. */
    private void save(GhostStore store) {
        savedRevision = store.revision();
        lastSave = Util.getMillis();
        if (!ConfigManager.get().persistence) {
            return;
        }
        Path file = fileFor(store.key());

        if (store.size() == 0) {
            submit(() -> Files.deleteIfExists(file));
            return;
        }

        ListTag list = new ListTag();
        for (Ghost ghost : store.view()) {
            list.add(GhostSerializer.write(ghost.snapshot()));
        }
        CompoundTag root = new CompoundTag();
        root.putInt("format", FORMAT_VERSION);
        root.putInt("dataVersion", currentDataVersion());
        root.put("ghosts", list);

        int count = list.size();
        submit(() -> {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            NbtIo.writeCompressed(root, tmp);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            HtzDistanceClient.debug("Saved {} ghosts to {}", count, file);
        });
    }

    private interface IoTask {
        void run() throws IOException;
    }

    private void submit(IoTask task) {
        if (io.isShutdown()) {
            runQuietly(task);
            return;
        }
        io.execute(() -> runQuietly(task));
    }

    private static void runQuietly(IoTask task) {
        try {
            task.run();
        } catch (IOException | RuntimeException e) {
            HtzDistanceClient.LOGGER.warn("Ghost file IO failed", e);
        }
    }

    static Path fileFor(WorldKey key) {
        Path root = Minecraft.getInstance().gameDirectory.toPath().resolve(HtzDistanceClient.MOD_ID);
        return root.resolve(sanitize(key.server())).resolve(sanitize(key.dimension())).resolve("ghosts.dat");
    }

    private static String sanitize(String name) {
        String safe = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return safe.isEmpty() ? "_" : safe;
    }

    private static int currentDataVersion() {
        return SharedConstants.getCurrentVersion().dataVersion().version();
    }
}
