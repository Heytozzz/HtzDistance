package com.heytozzz.htzdistance.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.heytozzz.htzdistance.HtzDistanceClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Loads and saves {@link HtzConfig} as {@code config/htzdistance.json}.
 *
 * <p>All access happens on the client thread; the current instance is replaced as a whole on
 * reload, so readers can keep a reference for the duration of one operation.
 */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeSpecialFloatingPointValues()
            .create();

    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve(HtzDistanceClient.MOD_ID + ".json");

    private static volatile HtzConfig config = new HtzConfig();

    private ConfigManager() {
    }

    public static HtzConfig get() {
        return config;
    }

    /**
     * Reads the config file, creating it with defaults when missing. A broken file is kept as
     * {@code htzdistance.json.broken} and replaced with defaults.
     */
    public static void load() {
        HtzConfig loaded = null;
        boolean needsSave = false;

        if (Files.exists(FILE)) {
            try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                loaded = GSON.fromJson(reader, HtzConfig.class);
            } catch (IOException | JsonParseException e) {
                HtzDistanceClient.LOGGER.error("Could not read config {}, using defaults", FILE, e);
                backupBrokenFile();
            }
        }

        if (loaded == null) {
            loaded = new HtzConfig();
            needsSave = true;
        }
        needsSave |= loaded.sanitize();

        config = loaded;
        if (needsSave) {
            save();
        }
    }

    /** Writes the current config atomically (temp file + move). */
    public static void save() {
        Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
            try {
                Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            HtzDistanceClient.LOGGER.error("Could not save config {}", FILE, e);
        }
    }

    /** Deep copy of the current config, for editing without touching the live instance. */
    public static HtzConfig copy() {
        return GSON.fromJson(GSON.toJsonTree(config), HtzConfig.class);
    }

    /** Replaces the whole config (used by the config screen), sanitizes and saves it. */
    public static void apply(HtzConfig newConfig) {
        newConfig.sanitize();
        config = newConfig;
        save();
    }

    private static void backupBrokenFile() {
        try {
            Files.move(FILE, FILE.resolveSibling(FILE.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            HtzDistanceClient.LOGGER.warn("Could not back up broken config {}", FILE, e);
        }
    }
}
