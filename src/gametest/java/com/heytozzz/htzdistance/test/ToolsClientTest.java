package com.heytozzz.htzdistance.test;

import com.heytozzz.htzdistance.GhostStats;
import com.heytozzz.htzdistance.HtzDistanceClient;
import com.heytozzz.htzdistance.config.ConfigManager;
import com.heytozzz.htzdistance.config.HtzConfig;
import com.heytozzz.htzdistance.ghost.GhostManager;
import com.heytozzz.htzdistance.ghost.GhostStore;
import com.heytozzz.htzdistance.render.GhostRenderer;
import com.heytozzz.htzdistance.tools.DebugStats;
import com.heytozzz.htzdistance.tools.GhostToggle;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
//? if >=26.1 {
/*import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
*///?} else {
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
//?}
//? if >=1.21.10 {
/*import com.heytozzz.htzdistance.tools.DebugOverlay;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
*///?}

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.heytozzz.htzdistance.test.TestSupport.assertGhosts;
import static com.heytozzz.htzdistance.test.TestSupport.check;
import static com.heytozzz.htzdistance.test.TestSupport.tp;

/**
 * Checks the player tools: the show/hide key, the {@code /htzdistance} command and the F3 lines.
 */
public final class ToolsClientTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> client.options.renderDistance().set(6));
        TestSupport.resetConfig(context, config -> config.persistence = true);

        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            TestSupport.waitForChunks(world);
            TestServerContext server = world.getServer();
            // Earlier tests used a world with the same name: start without their saved ghosts.
            command(context, "htzdistance clear all");
            assertGhosts(context, 0, "no ghosts left from earlier tests");

            BlockPos base = TestSupport.surface(server);
            int x = base.getX();
            int y = base.getY();
            int z = base.getZ();

            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("time set noon");
            server.runCommand(tp(x + 0.5, y, z + 6.0));
            TestSupport.spawnTestEntities(server, x, y, z + 10);
            context.waitTicks(60);
            TestSupport.waitForChunks(world);

            server.runCommand(tp(x + 0.5, y, z - 10.0));
            context.waitTicks(30);
            assertGhosts(context, 3, "three ghosts at 20 blocks");
            check(rendered(context) == 3, "three ghosts drawn");

            // F3 lines.
            List<String> lines = context.computeOnClient(client -> DebugStats.lines());
            HtzDistanceClient.LOGGER.info("[gametest] F3 lines: {}", lines);
            check(lines.size() == 2 && lines.get(0).contains("3 holograms") && lines.get(0).contains("drawn 3"),
                    "F3 line shows the ghost counts");
            //? if >=1.21.10 {
            /*check(context.computeOnClient(client -> DebugScreenEntries.getEntry(DebugOverlay.ENTRY_ID) != null),
                    "F3 entry registered");
            check(context.computeOnClient(client -> client.debugEntries.getStatus(DebugOverlay.ENTRY_ID) != DebugScreenEntryStatus.NEVER),
                    "F3 entry switched on");
            *///?}
            context.getInput().pressKey(InputConstants.KEY_F3);
            context.waitTicks(3);
            context.takeScreenshot("htzdistance-tools-01-f3");
            context.getInput().pressKey(InputConstants.KEY_F3);
            context.waitTicks(2);

            // Toggle key.
            context.getInput().pressKey(GhostToggle.key());
            context.waitTicks(3);
            check(context.computeOnClient(client -> GhostToggle.isHidden()), "key pauses the mod");
            // The state is saved: reading the config file again keeps the mod off.
            check(context.computeOnClient(client -> {
                ConfigManager.load();
                return GhostToggle.isHidden() && !ConfigManager.get().enabled;
            }), "pause survives reloading the config file");
            check(context.computeOnClient(client -> GhostToggle.stateMessage().getString()).equals("Htz: Holograms OFF"),
                    "action bar message");
            check(rendered(context) == 0, "hidden ghosts are not drawn");
            assertGhosts(context, 3, "hidden ghosts are kept");
            context.takeScreenshot("htzdistance-tools-02-hidden");
            long passes = context.computeOnClient(client -> GhostStats.CULLING_PASSES.get());
            context.waitTicks(10);
            check(context.computeOnClient(client -> GhostStats.CULLING_PASSES.get()) == passes, "culling thread idle while paused");
            // Paused: real entities coming back are not reconciled...
            server.runCommand(tp(x + 0.5, y, z + 6.0));
            context.waitTicks(30);
            assertGhosts(context, 3, "paused mod does not reconcile");
            context.getInput().pressKey(GhostToggle.key());
            context.waitTicks(3);
            check(!context.computeOnClient(client -> GhostToggle.isHidden()), "key resumes the mod");
            // ...until it resumes.
            assertGhosts(context, 0, "resume replaces ghosts whose real entity is back");
            server.runCommand(tp(x + 0.5, y, z - 10.0));
            context.waitTicks(30);
            assertGhosts(context, 3, "ghosts captured again after resuming");
            check(rendered(context) == 3, "ghosts drawn again");

            // Config command.
            command(context, "htzdistance config");
            command(context, "htzdistance config maxRenderDistance 50");
            check(context.computeOnClient(client -> ConfigManager.get().maxRenderDistance) == 50.0, "config: number option");
            command(context, "htzdistance config maxRenderDistance 1");
            check(context.computeOnClient(client -> ConfigManager.get().maxRenderDistance) == 8.0, "config: value clamped like the file");
            command(context, "htzdistance config itemFrame.threshold 9.5");
            check(context.computeOnClient(client -> ConfigManager.get().itemFrame.threshold) == 9.5, "config: per-type option");
            command(context, "htzdistance config occlusionCulling false");
            check(!context.computeOnClient(client -> ConfigManager.get().occlusionCulling), "config: boolean option");
            command(context, "htzdistance config hologramStyle tint");
            check(context.computeOnClient(client -> ConfigManager.get().hologramStyle) == HtzConfig.GhostStyle.TINT, "config: enum option");
            command(context, "htzdistance config tintColor #FF8000");
            check(context.computeOnClient(client -> ConfigManager.get().tintColor) == 0xFF8000, "config: color option");
            command(context, "htzdistance config translucentAlpha 0.5");
            check(context.computeOnClient(client -> ConfigManager.get().translucentAlpha) == 0.5f, "config: float option");
            commandFails(context, "htzdistance config occlusionCulling maybe");
            commandFails(context, "htzdistance config noSuchOption 1");
            command(context, "htzdistance config reset");
            check(context.computeOnClient(client -> {
                HtzConfig config = ConfigManager.get();
                HtzConfig defaults = new HtzConfig();
                return config.maxRenderDistance == defaults.maxRenderDistance && config.occlusionCulling
                        && config.itemFrame.threshold == defaults.itemFrame.threshold
                        && config.hologramStyle == HtzConfig.GhostStyle.NORMAL;
            }), "config: reset to defaults");

            // Toggle command.
            command(context, "htzdistance toggle");
            context.waitTicks(2);
            check(context.computeOnClient(client -> GhostToggle.isHidden()), "command hides the ghosts");
            command(context, "htzdistance toggle");
            context.waitTicks(2);
            check(rendered(context) == 3, "command shows the ghosts again");
            command(context, "htzdistance stats");

            // Clear within a radius: the armor stand is 20.5 blocks away, the frames about 21.
            command(context, "htzdistance clear 5");
            assertGhosts(context, 3, "nothing within 5 blocks");
            command(context, "htzdistance clear 20.75");
            assertGhosts(context, 2, "armor stand within 20.75 blocks removed");

            // Clear the dimension.
            command(context, "htzdistance clear");
            assertGhosts(context, 0, "dimension cleared");
            context.waitTicks(10);
            check(rendered(context) == 0, "nothing drawn after clear");

            // Clear all: the server's saved folder is deleted.
            server.runCommand(tp(x + 0.5, y, z + 6.0));
            context.waitTicks(30);
            server.runCommand(tp(x + 0.5, y, z - 10.0));
            context.waitTicks(30);
            assertGhosts(context, 3, "ghosts captured again");
            command(context, "htzdistance clear 20.75");
            context.waitTicks(10);
            Path serverDir = context.computeOnClient(client -> serverDir(client));
            check(Files.isDirectory(serverDir), "saved ghosts written after a clear: " + serverDir);
            command(context, "htzdistance clear all");
            assertGhosts(context, 0, "clear all empties the store");
            context.waitTicks(10);
            check(!Files.exists(serverDir), "clear all deletes the saved ghosts: " + serverDir);
        }

        context.runOnClient(client -> GhostToggle.setHidden(false));
        HtzDistanceClient.LOGGER.info("[gametest] ToolsClientTest passed");
    }

    private static int rendered(ClientGameTestContext context) {
        context.waitTicks(2);
        return context.computeOnClient(client -> GhostRenderer.lastFrameRendered());
    }

    /** Runs a client command the same way chat does. */
    private static void command(ClientGameTestContext context, String command) {
        context.runOnClient(client -> {
            FabricClientCommandSource source = (FabricClientCommandSource) client.getConnection().getSuggestionsProvider();
            try {
                //? if >=26.1 {
                /*ClientCommands.getActiveDispatcher().execute(command, source);
                *///?} else {
                ClientCommandManager.getActiveDispatcher().execute(command, source);
                //?}
            } catch (CommandSyntaxException e) {
                throw new AssertionError("Command failed: /" + command, e);
            }
        });
        HtzDistanceClient.LOGGER.info("[gametest] ran /{}", command);
    }

    private static void commandFails(ClientGameTestContext context, String command) {
        boolean failed = context.computeOnClient(client -> {
            FabricClientCommandSource source = (FabricClientCommandSource) client.getConnection().getSuggestionsProvider();
            try {
                //? if >=26.1 {
                /*ClientCommands.getActiveDispatcher().execute(command, source);
                *///?} else {
                ClientCommandManager.getActiveDispatcher().execute(command, source);
                //?}
                return false;
            } catch (CommandSyntaxException e) {
                return true;
            }
        });
        check(failed, "/" + command + " is rejected");
    }

    private static Path serverDir(Minecraft client) {
        GhostStore store = GhostManager.get().store();
        String server = store == null ? "_" : store.key().server();
        String safe = server.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return client.gameDirectory.toPath().resolve(HtzDistanceClient.MOD_ID).resolve(safe);
    }
}
