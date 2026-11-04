package com.heytozzz.htzdistance;

import com.heytozzz.htzdistance.config.ConfigManager;
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
        LOGGER.info("HtzDistance {} initialized for Minecraft {}", VERSION, MINECRAFT);
    }
}
