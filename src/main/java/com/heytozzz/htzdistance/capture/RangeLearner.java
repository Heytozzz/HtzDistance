package com.heytozzz.htzdistance.capture;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.heytozzz.htzdistance.HtzDistanceClient;
import com.heytozzz.htzdistance.ghost.GhostKind;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Learns the server's real tracking range per entity kind and per server.
 *
 * <p>The server removes an entity on the first tick the horizontal distance goes above its
 * range, and sends it again on the first tick it is back within. So removals seen while the
 * player walks away cluster just above the range, and additions seen while the player walks
 * towards the entity cluster just below it. The estimate is the midpoint of both medians.
 * Samples taken right after a teleport (or with the player standing still) are ignored.
 *
 * <p>Client thread only. Learned data is stored in {@code config/htzdistance-learned.json}.
 */
public final class RangeLearner {
    private static final RangeLearner INSTANCE = new RangeLearner();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve(HtzDistanceClient.MOD_ID + "-learned.json");

    /** Samples kept per kind and direction. */
    private static final int MAX_SAMPLES = 32;
    /** Samples needed in each direction before the estimate is used. */
    public static final int MIN_SAMPLES = 3;
    /** Player trail length in ticks (one second). */
    private static final int TRAIL_TICKS = 20;
    /** A single-tick jump larger than this is a teleport. */
    private static final double TELEPORT_DISTANCE = 4.0;
    /** Minimum change of distance over the trail to count as walking away or towards. */
    private static final double MIN_APPROACH = 0.2;

    /** Stored per server and kind. */
    public static final class Samples {
        public Deque<Double> removals = new ArrayDeque<>();
        public Deque<Double> additions = new ArrayDeque<>();
    }

    private Map<String, EnumMap<GhostKind, Samples>> data = new HashMap<>();
    /** Estimates computed since the last change, keyed by "server|kind". */
    private final Map<String, OptionalDouble> estimateCache = new HashMap<>();
    private final double[] trailX = new double[TRAIL_TICKS];
    private final double[] trailZ = new double[TRAIL_TICKS];
    private int trailSize;
    private int trailHead;
    private boolean dirty;

    private RangeLearner() {
    }

    public static RangeLearner get() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------ player trail

    /** Called once per client tick with the player position. */
    public void tickPlayer(@Nullable LocalPlayer player) {
        if (player == null) {
            trailSize = 0;
            return;
        }
        double x = player.getX();
        double z = player.getZ();
        if (trailSize > 0) {
            int last = (trailHead + TRAIL_TICKS - 1) % TRAIL_TICKS;
            if (Math.abs(x - trailX[last]) > TELEPORT_DISTANCE || Math.abs(z - trailZ[last]) > TELEPORT_DISTANCE) {
                // Restart the trail: positions before a teleport say nothing about walking.
                trailSize = 0;
            }
        }
        trailX[trailHead] = x;
        trailZ[trailHead] = z;
        trailHead = (trailHead + 1) % TRAIL_TICKS;
        trailSize = Math.min(trailSize + 1, TRAIL_TICKS);
    }

    /** Horizontal distance from the oldest trail position to the entity, or NaN. */
    private double oldDistance(Entity entity) {
        if (trailSize < 5) {
            return Double.NaN;
        }
        int oldest = (trailHead + TRAIL_TICKS - trailSize) % TRAIL_TICKS;
        return RangeClassifier.horizontalDistance(trailX[oldest], trailZ[oldest], entity.getX(), entity.getZ());
    }

    // ------------------------------------------------------------------ samples

    /** The server stopped sending the entity (left range or destroyed). */
    public void onRemoved(String server, GhostKind kind, Entity entity, double distance) {
        double before = oldDistance(entity);
        if (!Double.isNaN(before) && distance - before >= MIN_APPROACH) {
            add(samples(server, kind).removals, distance);
            HtzDistanceClient.debug("Learning: {} removed at {} while walking away", kind, String.format(java.util.Locale.ROOT, "%.2f", distance));
        }
    }

    /** The server sent the entity again. */
    public void onAdded(String server, GhostKind kind, Entity entity, double distance) {
        double before = oldDistance(entity);
        if (!Double.isNaN(before) && before - distance >= MIN_APPROACH) {
            add(samples(server, kind).additions, distance);
            HtzDistanceClient.debug("Learning: {} added at {} while approaching", kind, String.format(java.util.Locale.ROOT, "%.2f", distance));
        }
    }

    private void add(Deque<Double> deque, double distance) {
        if (distance < 1.0 || distance > 512.0) {
            return;
        }
        deque.addLast(distance);
        while (deque.size() > MAX_SAMPLES) {
            deque.removeFirst();
        }
        dirty = true;
        estimateCache.clear();
    }

    private Samples samples(String server, GhostKind kind) {
        return data.computeIfAbsent(server, s -> new EnumMap<>(GhostKind.class)).computeIfAbsent(kind, k -> new Samples());
    }

    /** Learned range for the server and kind, if there are enough samples. */
    public OptionalDouble estimate(@Nullable String server, GhostKind kind) {
        if (server == null) {
            return OptionalDouble.empty();
        }
        return estimateCache.computeIfAbsent(server + "|" + kind.name(), key -> computeEstimate(server, kind));
    }

    private OptionalDouble computeEstimate(String server, GhostKind kind) {
        EnumMap<GhostKind, Samples> byKind = data.get(server);
        Samples samples = byKind == null ? null : byKind.get(kind);
        if (samples == null || samples.removals.size() < MIN_SAMPLES || samples.additions.size() < MIN_SAMPLES) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of((median(samples.removals) + median(samples.additions)) / 2.0);
    }

    /** Number of samples (removals, additions) for display. */
    public int[] sampleCounts(@Nullable String server, GhostKind kind) {
        EnumMap<GhostKind, Samples> byKind = server == null ? null : data.get(server);
        Samples samples = byKind == null ? null : byKind.get(kind);
        return samples == null ? new int[]{0, 0} : new int[]{samples.removals.size(), samples.additions.size()};
    }

    public void reset(@Nullable String server) {
        if (server != null && data.remove(server) != null) {
            dirty = true;
            estimateCache.clear();
            save();
        }
    }

    private static double median(Deque<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    // ------------------------------------------------------------------ storage

    public void load() {
        if (!Files.isRegularFile(FILE)) {
            return;
        }
        Type type = new TypeToken<Map<String, EnumMap<GhostKind, Samples>>>() { }.getType();
        try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            Map<String, EnumMap<GhostKind, Samples>> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                loaded.values().removeIf(v -> v == null);
                for (EnumMap<GhostKind, Samples> byKind : loaded.values()) {
                    byKind.values().removeIf(v -> v == null);
                    for (Samples s : byKind.values()) {
                        if (s.removals == null) {
                            s.removals = new ArrayDeque<>();
                        }
                        if (s.additions == null) {
                            s.additions = new ArrayDeque<>();
                        }
                    }
                }
                data = new HashMap<>(loaded);
                estimateCache.clear();
            }
        } catch (IOException | JsonParseException e) {
            HtzDistanceClient.LOGGER.warn("Could not read learned ranges from {}", FILE, e);
        }
    }

    public void save() {
        if (!dirty) {
            return;
        }
        dirty = false;
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            HtzDistanceClient.LOGGER.warn("Could not save learned ranges to {}", FILE, e);
        }
    }
}
