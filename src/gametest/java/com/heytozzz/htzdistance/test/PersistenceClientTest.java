package com.heytozzz.htzdistance.test;

import com.heytozzz.htzdistance.HtzDistanceClient;
import com.heytozzz.htzdistance.ghost.GhostKind;
import com.heytozzz.htzdistance.ghost.GhostManager;
import com.heytozzz.htzdistance.ghost.GhostStore;
import com.heytozzz.htzdistance.render.GhostRenderer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.saveddata.maps.MapId;

import static com.heytozzz.htzdistance.test.TestSupport.assertGhosts;
import static com.heytozzz.htzdistance.test.TestSupport.check;
import static com.heytozzz.htzdistance.test.TestSupport.tp;

/**
 * Ghosts survive leaving and re-joining the world when persistence is on, including the map
 * contents of a framed map the client never receives again while out of range.
 */
public final class PersistenceClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            client.options.renderDistance().set(6);
        });
        TestSupport.resetConfig(context, config -> config.persistence = true);

        TestWorldSave save;
        int x;
        int y;
        int z;
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            TestSupport.waitForChunks(world);
            save = world.getWorldSave();
            BlockPos base = TestSupport.surface(world.getServer());
            x = base.getX();
            y = base.getY();
            z = base.getZ();
            world.getServer().runCommand("gamerule doDaylightCycle false");
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand(tp(x + 0.5, y, z + 6.0));
            TestSupport.spawnTestEntities(world.getServer(), x, y, z + 10);
            context.waitTicks(60);
            world.getServer().runCommand(tp(x + 0.5, y, z - 10.0));
            context.waitTicks(30);
            assertGhosts(context, 3, "ghosts before leaving the world");
        }
        context.waitTicks(20);

        try (TestSingleplayerContext world = save.open()) {
            TestSupport.waitForChunks(world);
            context.waitTicks(20);
            assertGhosts(context, 3, "ghosts loaded from disk after re-joining");
            int mapId = context.computeOnClient(client -> {
                GhostStore store = GhostManager.get().store();
                return store.view().stream()
                        .filter(g -> g.kind() == GhostKind.GLOW_ITEM_FRAME && g.snapshot().map() != null)
                        .map(g -> g.snapshot().map().mapId())
                        .findFirst().orElse(-1);
            });
            check(mapId >= 0, "persisted ghost frame still has its map");
            check(context.computeOnClient(client -> GhostRenderer.lastFrameRendered()) == 3, "persisted ghosts rendered");
            check(context.computeOnClient(client -> client.level.getMapData(new MapId(mapId)) != null),
                    "map contents restored into the client level");
            context.runOnClient(client -> client.options.fov().set(30));
            context.waitTicks(3);
            context.takeScreenshot("htzdistance-06-persisted-ghosts");
            context.runOnClient(client -> client.options.fov().set(70));

            world.getServer().runCommand(tp(x + 0.5, y, z + 6.0));
            context.waitTicks(30);
            assertGhosts(context, 0, "persisted ghosts replaced by real entities");
        }

        TestSupport.resetConfig(context, config -> { });
        HtzDistanceClient.LOGGER.info("[gametest] PersistenceClientTest passed");
    }
}
