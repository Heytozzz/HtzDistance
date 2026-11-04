package com.heytozzz.htzdistance;

import com.heytozzz.htzdistance.config.ConfigManager;
import com.heytozzz.htzdistance.culling.CullingWorker;
import com.heytozzz.htzdistance.ghost.GhostManager;
import com.heytozzz.htzdistance.ghost.GhostStore;
import com.heytozzz.htzdistance.persist.GhostPersistence;
import com.heytozzz.htzdistance.render.RenderHook;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entry point. Wires capture, ghost storage, rendering and culling together.
 */
public final class HtzDistanceClient implements ClientModInitializer {
    public static final String MOD_ID = "htzdistance";
    public static final Logger LOGGER = LoggerFactory.getLogger("HtzDistance");
    public static final String VERSION = /*$ mod_version*/ "0.1.0";
    public static final String MINECRAFT = /*$ minecraft*/ "1.21.8";

    @Override
    public void onInitializeClient() {
        ConfigManager.load();
        GhostManager.get().register();
        GhostManager.get().addListener(new GhostManager.StoreListener() {
            @Override
            public void onStoreClosing(GhostStore store) {
                CullingWorker.get().clear();
            }
        });
        new GhostPersistence().register();
        RenderHook.register();
        CullingWorker.get().start();
        LOGGER.info("HtzDistance {} initialized for Minecraft {}", VERSION, MINECRAFT);
    }

    /** Logs at INFO level when debug logging is enabled in the config. */
    public static void debug(String message, Object... args) {
        if (ConfigManager.get().debugLogging) {
            LOGGER.info("[debug] " + message, args);
        }
    }
}
