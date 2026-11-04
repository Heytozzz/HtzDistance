package com.heytozzz.htzdistance.test;

import com.heytozzz.htzdistance.HtzDistanceClient;
import com.heytozzz.htzdistance.config.ConfigManager;
import com.heytozzz.htzdistance.config.HtzConfig;
import com.heytozzz.htzdistance.ghost.Ghost;
import com.heytozzz.htzdistance.ghost.GhostKind;
import com.heytozzz.htzdistance.ghost.GhostManager;
import com.heytozzz.htzdistance.ghost.GhostStore;
import com.heytozzz.htzdistance.render.GhostRenderer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
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

/**
 * End-to-end check of the ghost lifecycle in a singleplayer world.
 *
 * <p>The test mixin {@code TrackedEntityRangeMixin} makes the integrated server behave like the
 * target server: armor stands are tracked up to 16 blocks, item frames up to 8. The mod runs
 * with its default config (thresholds 16 / 8, margin 1.5).
 */
public final class GhostLifecycleClientTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            client.options.renderDistance().set(6);
            client.options.hideGui = true;
        });
        updateConfig(context, config -> {
            config.inRangeTimeoutSeconds = 2.0;
            config.debugLogging = true;
        });

        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            TestServerContext server = world.getServer();

            BlockPos base = server.computeOnServer(s -> {
                ServerLevel level = s.overworld();
                return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, level.getSharedSpawnPos());
            });
            int x = base.getX();
            int y = base.getY();
            int z = base.getZ();
            int ez = z + 10;

            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("time set noon");
            server.runCommand(tp(x + 0.5, y, z + 6.0));
            spawnTestEntities(server, x, y, ez, z);

            context.waitTicks(60);
            world.getClientWorld().waitForChunksRender();
            context.takeScreenshot("htzdistance-01-real-entities");
            assertGhosts(context, 0, "no ghosts while everything is in range");

            // 1) 12-13 blocks away: frames left their 8-block range, the armor stand did not.
            server.runCommand(tp(x + 0.5, y, z - 2.5));
            context.waitTicks(30);
            assertGhosts(context, 2, "frame ghosts at 13 blocks");

            // 2) 20 blocks away: everything is a ghost and gets drawn.
            server.runCommand(tp(x + 0.5, y, z - 10.0));
            context.waitTicks(30);
            assertGhosts(context, 3, "all ghosts at 20 blocks");
            check(context.computeOnClient(client -> {
                GhostStore store = GhostManager.get().store();
                return store != null && store.view().stream()
                        .anyMatch(g -> g.kind() == GhostKind.GLOW_ITEM_FRAME && g.snapshot().map() != null);
            }), "map data captured with the glow item frame");
            check(context.computeOnClient(client -> GhostRenderer.lastFrameRendered()) == 3, "three ghosts rendered");
            // Zoom in so the ghost styles are visible in the screenshots.
            context.runOnClient(client -> client.options.fov().set(30));
            context.waitTicks(3);
            context.takeScreenshot("htzdistance-02-ghosts-normal");

            updateConfig(context, config -> config.ghostStyle = HtzConfig.GhostStyle.TINT);
            context.waitTicks(3);
            context.takeScreenshot("htzdistance-03-ghosts-tint");
            updateConfig(context, config -> config.ghostStyle = HtzConfig.GhostStyle.TRANSLUCENT);
            context.waitTicks(3);
            context.takeScreenshot("htzdistance-04-ghosts-translucent");
            updateConfig(context, config -> config.ghostStyle = HtzConfig.GhostStyle.NORMAL);

            // Occlusion culling: a wall between the player and the ghosts hides them.
            String wall = String.format(Locale.ROOT, "fill %d %d %d %d %d %d", x - 8, y, z - 6, x + 8, y + 5, z - 6);
            server.runCommand(wall + " stone");
            context.waitTicks(10);
            check(context.computeOnClient(client -> GhostRenderer.lastFrameRendered()) == 0, "ghosts behind a wall are culled");
            context.takeScreenshot("htzdistance-05-culled-behind-wall");
            server.runCommand(wall + " air");
            context.waitTicks(10);
            check(context.computeOnClient(client -> GhostRenderer.lastFrameRendered()) == 3, "ghosts visible again without the wall");
            context.runOnClient(client -> client.options.fov().set(70));

            // 3) Walk back: the real entities are resent and replace the ghosts.
            server.runCommand(tp(x + 0.5, y, z + 6.0));
            context.waitTicks(30);
            assertGhosts(context, 0, "ghosts replaced by real entities");

            // 4) Destroyed close to the player: no ghost.
            server.runCommand("kill @e[type=armor_stand]");
            context.waitTicks(20);
            assertGhosts(context, 0, "destroyed armor stand is not kept");

            // 5) Destroyed while the player is away: removed after the in-range timeout.
            server.runCommand(tp(x + 0.5, y, z - 10.0));
            context.waitTicks(30);
            assertGhosts(context, 2, "frames are ghosts again");
            server.runCommand("kill @e[type=item_frame]");
            server.runCommand("kill @e[type=glow_item_frame]");
            server.runCommand(tp(x + 0.5, y, z + 6.0));
            context.waitTicks(20);
            assertGhosts(context, 2, "ghosts survive inside the timeout");
            context.waitTicks(40);
            assertGhosts(context, 0, "ghosts removed after the in-range timeout");
        }

        HtzDistanceClient.LOGGER.info("[gametest] GhostLifecycleClientTest passed");
    }

    private static void spawnTestEntities(TestServerContext server, int x, int y, int ez, int z) {
        // Armor stand with gear and a custom pose, facing the player (north).
        server.runCommand(String.format(Locale.ROOT,
                "summon armor_stand %d.5 %d %d.5 {ShowArms:1b,NoBasePlate:1b,Rotation:[180f,0f],"
                        + "equipment:{head:{id:\"minecraft:diamond_helmet\"},chest:{id:\"minecraft:golden_chestplate\"},"
                        + "mainhand:{id:\"minecraft:diamond_sword\"}},Pose:{RightArm:[-90f,0f,0f]}}",
                x, y, ez));
        // Item frame with a rotated apple.
        server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d stone", x - 2, y + 1, ez + 1));
        server.runCommand(String.format(Locale.ROOT,
                "summon item_frame %d %d %d {Facing:2b,ItemRotation:1b,Item:{id:\"minecraft:apple\",count:1}}",
                x - 2, y + 1, ez));
        // Glow item frame with a checkered map.
        server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d stone", x + 2, y + 1, ez + 1));
        server.runOnServer(s -> {
            ServerLevel level = s.overworld();
            ItemStack map = MapItem.create(level, x, z, (byte) 0, false, false);
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

    private static void updateConfig(ClientGameTestContext context, Consumer<HtzConfig> change) {
        context.runOnClient(client -> {
            HtzConfig config = ConfigManager.get();
            change.accept(config);
            ConfigManager.apply(config);
        });
    }

    private static String tp(double x, double y, double z) {
        // Yaw 0 faces south (+Z), where the test entities are.
        return String.format(Locale.ROOT, "tp @p %.2f %d %.2f 0 0", x, (int) y, z);
    }

    private static void assertGhosts(ClientGameTestContext context, int expected, String what) {
        int actual = context.computeOnClient(client -> {
            GhostStore store = GhostManager.get().store();
            return store == null ? -1 : store.size();
        });
        if (actual != expected) {
            String kinds = context.computeOnClient(client -> {
                GhostStore store = GhostManager.get().store();
                return store == null ? "none" : store.view().stream().map(Ghost::kind).toList().toString();
            });
            throw new AssertionError(what + ": expected " + expected + " ghosts, got " + actual + " " + kinds);
        }
        HtzDistanceClient.LOGGER.info("[gametest] OK: {} ({} ghosts)", what, actual);
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError("Check failed: " + what);
        }
        HtzDistanceClient.LOGGER.info("[gametest] OK: {}", what);
    }
}
