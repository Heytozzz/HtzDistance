package com.heytozzz.htzdistance.test;

import com.heytozzz.htzdistance.HtzDistanceClient;
import com.heytozzz.htzdistance.config.ConfigManager;
import com.heytozzz.htzdistance.config.HtzConfig;
import com.heytozzz.htzdistance.ghost.Ghost;
import com.heytozzz.htzdistance.ghost.GhostManager;
import com.heytozzz.htzdistance.ghost.GhostStore;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.Locale;
import java.util.function.Consumer;

/** Shared helpers for the client game tests. */
final class TestSupport {
    private TestSupport() {
    }

    /** Surface position below the (only) player. */
    static BlockPos surface(TestServerContext server) {
        return server.computeOnServer(s -> {
            ServerLevel level = s.overworld();
            BlockPos player = s.getPlayerList().getPlayers().getFirst().blockPosition();
            return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, player);
        });
    }

    /** Waits until the client has rendered the chunks around the player. */
    static void waitForChunks(TestSingleplayerContext world) {
        //? if >=26.2 {
        /*world.getConnection().waitForChunksRender();
        *///?} else if >=26.1 {
        /*world.getClientLevel().waitForChunksRender();
        *///?} else {
        world.getClientWorld().waitForChunksRender();
        //?}
    }

    /**
     * Spawns an armor stand at (x, y, ez), an item frame with an apple at (x - 2, y + 1, ez) and a
     * glow item frame with a checkered map at (x + 2, y + 1, ez), all facing north.
     */
    static void spawnTestEntities(TestServerContext server, int x, int y, int ez) {
        server.runCommand(String.format(Locale.ROOT,
                "summon armor_stand %d.5 %d %d.5 {ShowArms:1b,NoBasePlate:1b,Rotation:[180f,0f],"
                        + "equipment:{head:{id:\"minecraft:diamond_helmet\"},chest:{id:\"minecraft:golden_chestplate\"},"
                        + "mainhand:{id:\"minecraft:diamond_sword\"}},Pose:{RightArm:[-90f,0f,0f]}}",
                x, y, ez));
        server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d stone", x - 2, y + 1, ez + 1));
        server.runCommand(String.format(Locale.ROOT,
                "summon item_frame %d %d %d {Facing:2b,ItemRotation:1b,Item:{id:\"minecraft:apple\",count:1}}",
                x - 2, y + 1, ez));
        server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d stone", x + 2, y + 1, ez + 1));
        server.runOnServer(s -> {
            ServerLevel level = s.overworld();
            ItemStack map = MapItem.create(level, x, ez, (byte) 0, false, false);
            MapItemSavedData data = MapItem.getSavedData(map, level);
            for (int px = 0; px < 128; px++) {
                for (int py = 0; py < 128; py++) {
                    data.setColor(px, py, (byte) (((px / 16 + py / 16) % 2 == 0) ? 18 * 4 + 2 : 29 * 4 + 2));
                }
            }
            GlowItemFrame frame = new GlowItemFrame(level, new BlockPos(x + 2, y + 1, ez), Direction.NORTH);
            frame.setItem(map);
            level.addFreshEntity(frame);
        });
    }

    /** Starts from the default config (with debug logging), then applies the change. */
    static void resetConfig(ClientGameTestContext context, Consumer<HtzConfig> change) {
        context.runOnClient(client -> {
            HtzConfig config = new HtzConfig();
            config.debugLogging = true;
            change.accept(config);
            ConfigManager.apply(config);
        });
    }

    static void updateConfig(ClientGameTestContext context, Consumer<HtzConfig> change) {
        context.runOnClient(client -> {
            HtzConfig config = ConfigManager.get();
            change.accept(config);
            ConfigManager.apply(config);
        });
    }

    /** Teleport command facing south (+Z), where the test entities are. */
    static String tp(double x, double y, double z) {
        return String.format(Locale.ROOT, "tp @p %.2f %d %.2f 0 0", x, (int) y, z);
    }

    static int ghostCount(ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            GhostStore store = GhostManager.get().store();
            return store == null ? -1 : store.size();
        });
    }

    static void assertGhosts(ClientGameTestContext context, int expected, String what) {
        int actual = ghostCount(context);
        if (actual != expected) {
            String kinds = context.computeOnClient(client -> {
                GhostStore store = GhostManager.get().store();
                return store == null ? "none" : store.view().stream().map(Ghost::kind).toList().toString();
            });
            throw new AssertionError(what + ": expected " + expected + " ghosts, got " + actual + " " + kinds);
        }
        HtzDistanceClient.LOGGER.info("[gametest] OK: {} ({} ghosts)", what, actual);
    }

    static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError("Check failed: " + what);
        }
        HtzDistanceClient.LOGGER.info("[gametest] OK: {}", what);
    }
}
