package com.heytozzz.htzdistance.selftest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Entry point of the self-test mod. Inert unless {@code htzdistance-selftest.trigger} exists in
 * the game directory; then it runs {@link SelfTestRun} once and quits the game.
 */
public final class SelfTestClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("HtzDistance SelfTest");
    public static final String TRIGGER = "htzdistance-selftest.trigger";

    @Override
    public void onInitializeClient() {
        Path trigger = FabricLoader.getInstance().getGameDir().resolve(TRIGGER);
        if (!Files.exists(trigger)) {
            return;
        }
        LOGGER.info("Self-test trigger found, the self-test will run once the title screen is shown");
        SelfTestRun run = new SelfTestRun(FabricLoader.getInstance().getGameDir());
        ClientTickEvents.END_CLIENT_TICK.register(run::tick);
    }
}
