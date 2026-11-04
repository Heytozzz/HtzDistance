package com.heytozzz.htzdistance.test;

import com.heytozzz.htzdistance.HtzDistanceClient;
import com.heytozzz.htzdistance.capture.RangeClassifier;
import com.heytozzz.htzdistance.ghost.GhostKind;
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
 * Learning mode: starting from wrong thresholds (30 blocks), walking away from and back to the
 * entities a few times must yield the server's real ranges (16 for armor stands, 8 for frames).
 */
public final class LearningClientTest implements FabricClientGameTest {
    private static final double TOLERANCE = 0.75;
    /** About 25 blocks at walking speed. */
    private static final int WALK_TICKS = 115;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> client.options.renderDistance().set(6));
        context.runOnClient(client -> client.options.toggleSprint().set(false));
        TestSupport.resetConfig(context, config -> {
            config.learningMode = true;
            for (GhostKind kind : GhostKind.values()) {
                config.settingsFor(kind).threshold = 30.0;
            }
        });

        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            TestSupport.waitForChunks(world);
            TestServerContext server = world.getServer();
            BlockPos base = TestSupport.surface(server);
            int x = base.getX();
            int y = base.getY();
            int z = base.getZ();
            server.runCommand(tp(x + 0.5, y, z + 6.0));
            TestSupport.spawnTestEntities(server, x, y, z + 10);
            context.waitTicks(40);

            // Walk like a player (movement keys, so the client sends its position every tick and
            // the server re-checks tracking each time): backwards away from the entities for
            // ~25 blocks, then forwards back, three times. Teleports would not work here: the
            // server only re-checks tracking for them when the player crosses a chunk section.
            for (int round = 0; round < 3; round++) {
                context.getInput().holdKeyFor(options -> options.keyDown, WALK_TICKS);
                context.getInput().holdKeyFor(options -> options.keyUp, WALK_TICKS);
                server.runCommand(tp(x + 0.5, y, z + 6.0));
                context.waitTicks(10);
            }

            for (GhostKind kind : GhostKind.values()) {
                double learned = context.computeOnClient(client -> RangeClassifier.threshold(kind));
                double expected = kind == GhostKind.ARMOR_STAND ? 16.0 : 8.0;
                check(Math.abs(learned - expected) <= TOLERANCE,
                        String.format(Locale.ROOT, "learned %s range %.2f is close to %.0f", kind, learned, expected));
            }

            // With the learned ranges, leaving range creates ghosts again (the configured 30 would not).
            server.runCommand(tp(x + 0.5, y, z - 10.0));
            context.waitTicks(30);
            assertGhosts(context, 3, "ghosts captured using the learned ranges");
        }

        TestSupport.resetConfig(context, config -> { });
        HtzDistanceClient.LOGGER.info("[gametest] LearningClientTest passed");
    }
}
