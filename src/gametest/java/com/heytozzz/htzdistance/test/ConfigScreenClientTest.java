package com.heytozzz.htzdistance.test;

import com.heytozzz.htzdistance.HtzDistanceClient;
import com.heytozzz.htzdistance.config.screen.ModMenuIntegration;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.Screen;

/**
 * Opens the config screen through the Mod Menu factory (YACL is present in dev runs) and takes
 * a screenshot of each category.
 */
public final class ConfigScreenClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.setScreen(() -> {
            Screen screen = new ModMenuIntegration().getModConfigScreenFactory().create(null);
            if (screen == null) {
                throw new AssertionError("Config screen factory returned null although YACL is installed");
            }
            return screen;
        });
        context.waitTicks(5);
        context.takeScreenshot("htzdistance-config-general");
        // YACL tabs are not plain buttons: click them at their position in the 854x480 window.
        String[] names = {"rendering", "storage"};
        int[] tabX = {424, 668};
        for (int i = 0; i < names.length; i++) {
            context.getInput().setCursorPos(tabX[i], 27);
            context.getInput().pressMouse(0);
            context.waitTicks(5);
            context.takeScreenshot("htzdistance-config-" + names[i]);
        }
        context.setScreen(() -> null);
        HtzDistanceClient.LOGGER.info("[gametest] ConfigScreenClientTest passed");
    }
}
