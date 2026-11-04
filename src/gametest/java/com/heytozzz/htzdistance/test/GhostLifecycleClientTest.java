package com.heytozzz.htzdistance.test;

import com.heytozzz.htzdistance.HtzDistanceClient;
import com.heytozzz.htzdistance.config.HtzConfig;
import com.heytozzz.htzdistance.ghost.GhostKind;
import com.heytozzz.htzdistance.ghost.GhostManager;
import com.heytozzz.htzdistance.ghost.GhostStore;
import com.heytozzz.htzdistance.render.GhostRenderer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;

import java.util.Locale;

import static com.heytozzz.htzdistance.test.TestSupport.assertGhosts;
import static com.heytozzz.htzdistance.test.TestSupport.check;
import static com.heytozzz.htzdistance.test.TestSupport.tp;

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
        TestSupport.resetConfig(context, config -> config.inRangeTimeoutSeconds = 2.0);

        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            TestServerContext server = world.getServer();

            BlockPos base = TestSupport.surface(server);
            int x = base.getX();
            int y = base.getY();
            int z = base.getZ();
            int ez = z + 10;

            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("time set noon");
            server.runCommand(tp(x + 0.5, y, z + 6.0));
            TestSupport.spawnTestEntities(server, x, y, ez);

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

            TestSupport.updateConfig(context, config -> config.ghostStyle = HtzConfig.GhostStyle.TINT);
            context.waitTicks(3);
            context.takeScreenshot("htzdistance-03-ghosts-tint");
            TestSupport.updateConfig(context, config -> config.ghostStyle = HtzConfig.GhostStyle.TRANSLUCENT);
            context.waitTicks(3);
            context.takeScreenshot("htzdistance-04-ghosts-translucent");
            TestSupport.updateConfig(context, config -> config.ghostStyle = HtzConfig.GhostStyle.NORMAL);

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
}
