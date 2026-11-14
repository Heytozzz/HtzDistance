package com.heytozzz.htzdistance.test;

import com.heytozzz.htzdistance.HtzDistanceClient;
import com.heytozzz.htzdistance.config.HtzConfig;
import com.heytozzz.htzdistance.ghost.GhostManager;
import com.heytozzz.htzdistance.ghost.GhostStore;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.OptionInstance;
import net.minecraft.core.BlockPos;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Records screenshot sequences for the README GIFs: walking away from armor stands, item frames
 * and paintings until they turn into holograms, then back until the real entities return. The
 * camera zooms in with the distance so the models stay the same size on screen.
 *
 * <p>Does nothing unless the environment variable {@code HTZDISTANCE_RECORD} is set, so normal
 * test runs are not slowed down. The screenshots are named {@code rec-<take>-<frame>}.
 */
public final class RecordingClientTest implements FabricClientGameTest {
    /** Distance (blocks) at which the base field of view (70) frames the models. */
    private static final double FRAMING_DISTANCE = 6.0;
    private static final double NEAR = 5.0;
    private static final double FAR = 24.0;
    private static final double STEP = 0.4;

    @Override
    public void runTest(ClientGameTestContext context) {
        if (System.getenv("HTZDISTANCE_RECORD") == null) {
            return;
        }
        context.runOnClient(client -> client.options.renderDistance().set(6));
        TestSupport.resetConfig(context, config -> {
            config.persistence = false;
            config.debugLogging = false;
        });

        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            TestSupport.waitForChunks(world);
            TestServerContext server = world.getServer();
            BlockPos base = TestSupport.surface(server);
            int x = base.getX();
            int y = base.getY();
            int z = base.getZ();
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("gamerule doWeatherCycle false");
            server.runCommand("weather clear");
            server.runCommand("time set 6000");
            server.runCommand("gamerule doMobSpawning false");
            server.runCommand("kill @e[type=!player]");

            buildScene(server, x, y, z);
            walkTo(context, server, x, y, z + 10 - NEAR);
            context.waitTicks(40);
            spawnModels(server, x, y, z);
            context.waitTicks(40);
            TestSupport.waitForChunks(world);
            // Flying in creative, without view bobbing: teleports do not shake the camera.
            server.runCommand("gamemode creative @p");
            context.waitTicks(5);
            context.runOnClient(client -> {
                client.options.bobView().set(false);
                if (client.player != null) {
                    client.player.getAbilities().flying = true;
                    client.player.onUpdateAbilities();
                }
            });
            context.waitTicks(5);
            // F1: no HUD, no hand.
            context.getInput().pressKey(InputConstants.KEY_F1);

            record(context, server, x, y, z, "normal", config -> {
            });
            record(context, server, x, y, z, "translucent", config -> config.hologramStyle = HtzConfig.GhostStyle.TRANSLUCENT);
            record(context, server, x, y, z, "tint", config -> {
                config.hologramStyle = HtzConfig.GhostStyle.TINT;
                config.tintColor = 0x55BBFF;
                config.tintOpacity = 0.75f;
            });
            record(context, server, x, y, z, "glowing", config -> config.glowing = true);
            // Without the mod: the entities just vanish.
            record(context, server, x, y, z, "off", config -> config.enabled = false);

            context.getInput().pressKey(InputConstants.KEY_F1);
        }
        setFov(context, 70);
        context.runOnClient(client -> client.options.bobView().set(true));
        HtzDistanceClient.LOGGER.info("[gametest] RecordingClientTest done");
    }

    /** One take: from close by (real entities) to far away (holograms) and back. */
    private static void record(ClientGameTestContext context, TestServerContext server, int x, int y, int z,
                               String take, Consumer<HtzConfig> style) {
        TestSupport.resetConfig(context, config -> {
            config.persistence = false;
            config.debugLogging = false;
            style.accept(config);
        });
        walkTo(context, server, x, y, z + 10 - NEAR);
        context.waitTicks(40);
        context.runOnClient(client -> {
            GhostStore store = GhostManager.get().store();
            if (store != null) {
                store.clear();
            }
        });
        int frame = 0;
        for (int i = 0; i < 6; i++) {
            frame = shoot(context, take, frame);
        }
        for (double d = NEAR; d <= FAR; d += STEP) {
            walkTo(context, server, x, y, z + 10 - d);
            frame = shoot(context, take, frame);
        }
        for (int i = 0; i < 8; i++) {
            frame = shoot(context, take, frame);
        }
        for (double d = FAR; d >= NEAR; d -= STEP) {
            walkTo(context, server, x, y, z + 10 - d);
            frame = shoot(context, take, frame);
        }
        for (int i = 0; i < 8; i++) {
            frame = shoot(context, take, frame);
        }
        HtzDistanceClient.LOGGER.info("[gametest] recorded take {} ({} frames)", take, frame);
    }

    /**
     * The field of view option only takes whole degrees, which makes the zoom jump. Each frame is
     * rendered at the next whole degree and named with the exact and the rendered value, so the
     * GIF script crops the small difference away: {@code rec-<take>-<frame>-<exact x1000>-<rendered>}.
     */
    private static int shoot(ClientGameTestContext context, String take, int frame) {
        context.waitTicks(1);
        context.takeScreenshot(String.format(Locale.ROOT, "rec-%s-%03d-%05d-%03d", take, frame,
                Math.round(exactFov * 1000.0), renderedFov));
        return frame + 1;
    }

    private static double exactFov = 70.0;
    private static int renderedFov = 70;

    /** Moves the player to (x + 0.5, y, pz) facing the models, and zooms to keep them framed. */
    private static void walkTo(ClientGameTestContext context, TestServerContext server, int x, int y, double pz) {
        server.runCommand(String.format(Locale.ROOT, "tp @p %.2f %d %.2f 0 4", x + 0.5, y, pz));
        double d = Math.max(FRAMING_DISTANCE, modelsZ - pz);
        double fov = Math.toDegrees(2.0 * Math.atan(Math.tan(Math.toRadians(35.0)) * FRAMING_DISTANCE / d));
        exactFov = Math.min(70.0, fov);
        renderedFov = (int) Math.ceil(exactFov - 1e-9);
        setFov(context, renderedFov);
    }

    /** Z of the wall the models stand against (set by buildScene). */
    private static double modelsZ;

    /** Sets the field of view, below the option's minimum of 30 if needed. */
    private static void setFov(ClientGameTestContext context, int fov) {
        context.runOnClient(client -> {
            OptionInstance<Integer> option = client.options.fov();
            try {
                Field value = OptionInstance.class.getDeclaredField("value");
                value.setAccessible(true);
                value.set(option, fov);
            } catch (ReflectiveOperationException e) {
                option.set(Math.max(30, fov));
            }
        });
    }

    private static void buildScene(TestServerContext server, int x, int y, int z) {
        modelsZ = z + 10;
        int w = z + 11;
        String[] commands = {
                // Ground: grass with a dirt path towards the wall.
                fill(x - 12, y - 1, z - 18, x + 12, y - 1, z + 14, "grass_block"),
                fill(x - 12, y, z - 18, x + 12, y + 8, z + 14, "air"),
                fill(x - 1, y - 1, z - 18, x + 1, y - 1, z + 9, "dirt_path"),
                fill(x - 2, y - 1, z + 3, x - 2, y - 1, z + 8, "coarse_dirt"),
                // Wall behind the models, with log pillars, a slab top and lanterns.
                fill(x - 6, y, w, x + 6, y + 4, w, "stone_bricks"),
                fill(x - 6, y, w + 1, x + 6, y + 4, w + 1, "stone_bricks"),
                setblock(x - 5, y + 1, w, "mossy_stone_bricks"),
                setblock(x - 1, y, w, "mossy_stone_bricks"),
                setblock(x + 4, y + 4, w, "cracked_stone_bricks"),
                setblock(x + 1, y + 3, w, "mossy_stone_bricks"),
                setblock(x + 6, y + 1, w, "mossy_stone_bricks"),
                fill(x - 7, y, w, x - 7, y + 5, w + 1, "spruce_log"),
                fill(x + 7, y, w, x + 7, y + 5, w + 1, "spruce_log"),
                fill(x - 7, y + 5, w, x + 7, y + 5, w, "spruce_slab"),
                fill(x - 7, y + 5, w + 1, x + 7, y + 5, w + 1, "spruce_slab"),
                setblock(x - 7, y + 6, w, "lantern"),
                setblock(x + 7, y + 6, w, "lantern"),
                // Planters along the wall.
                fill(x - 6, y, z + 10, x - 5, y, z + 10, "moss_block"),
                setblock(x - 6, y + 1, z + 10, "azalea"),
                setblock(x - 5, y + 1, z + 10, "flowering_azalea"),
                fill(x + 5, y, z + 10, x + 6, y, z + 10, "moss_block"),
                setblock(x + 5, y + 1, z + 10, "flowering_azalea"),
                setblock(x + 6, y + 1, z + 10, "azalea"),
                // Fence posts with lanterns along the path.
                setblock(x - 3, y, z + 4, "spruce_fence"),
                setblock(x - 3, y + 1, z + 4, "lantern"),
                setblock(x + 3, y, z + 4, "spruce_fence"),
                setblock(x + 3, y + 1, z + 4, "lantern"),
                setblock(x - 3, y, z - 4, "spruce_fence"),
                setblock(x - 3, y + 1, z - 4, "lantern"),
                setblock(x + 3, y, z - 4, "spruce_fence"),
                setblock(x + 3, y + 1, z - 4, "lantern"),
                // Trees on both sides.
                fill(x - 11, y + 3, z + 4, x - 7, y + 5, z + 8, "oak_leaves[persistent=true]"),
                fill(x - 10, y + 6, z + 5, x - 8, y + 6, z + 7, "oak_leaves[persistent=true]"),
                fill(x - 9, y, z + 6, x - 9, y + 5, z + 6, "oak_log"),
                fill(x + 7, y + 3, z + 2, x + 11, y + 5, z + 6, "birch_leaves[persistent=true]"),
                fill(x + 8, y + 6, z + 3, x + 10, y + 6, z + 5, "birch_leaves[persistent=true]"),
                fill(x + 9, y, z + 4, x + 9, y + 5, z + 4, "birch_log"),
        };
        for (String command : commands) {
            server.runCommand(command);
        }
        // Flowers and grass, scattered on both sides of the path.
        String[] plants = {"poppy", "dandelion", "cornflower", "oxeye_daisy", "allium", "azure_bluet", "short_grass", "short_grass", "lily_of_the_valley", "fern"};
        int[][] spots = {
                {-3, 7}, {-4, 2}, {-2, 0}, {-5, -3}, {-3, -8}, {-4, -12}, {-6, 5}, {-2, -5}, {-5, 9}, {-6, -7},
                {3, 8}, {4, 1}, {2, -1}, {5, -2}, {3, -9}, {4, -13}, {6, 6}, {2, -6}, {5, 9}, {6, -10},
                {-8, 0}, {8, -1}, {-7, -14}, {7, -15}, {-4, -16}, {4, -17},
        };
        for (int i = 0; i < spots.length; i++) {
            server.runCommand(setblock(x + spots[i][0], y, z + spots[i][1], plants[i % plants.length]));
        }
    }

    private static void spawnModels(TestServerContext server, int x, int y, int z) {
        String trim = "components:{\"minecraft:trim\":{material:\"minecraft:redstone\",pattern:\"minecraft:silence\"}}";
        server.runCommand(String.format(Locale.ROOT,
                "summon armor_stand %d.5 %d %d.5 {ShowArms:1b,NoBasePlate:1b,Rotation:[180f,0f],"
                        + "equipment:{head:{id:\"minecraft:netherite_helmet\",%s},chest:{id:\"minecraft:netherite_chestplate\",%s},"
                        + "legs:{id:\"minecraft:netherite_leggings\",%s},feet:{id:\"minecraft:netherite_boots\",%s},"
                        + "mainhand:{id:\"minecraft:netherite_sword\"},offhand:{id:\"minecraft:shield\"}},"
                        + "Pose:{RightArm:[-20f,0f,10f],LeftArm:[-10f,0f,-10f],Head:[5f,0f,0f]}}",
                x, y, z + 9, trim, trim, trim, trim));
        // Item frames on the left of the wall, paintings on the right (1x1 and 2x2).
        frame(server, "item_frame", x - 3, y + 2, z + 10, "minecraft:golden_apple", 0);
        frame(server, "item_frame", x - 4, y + 1, z + 10, "minecraft:diamond_sword", 1);
        frame(server, "glow_item_frame", x - 2, y + 1, z + 10, "minecraft:heart_of_the_sea", 0);
        painting(server, x + 2, y + 1, z + 10, "minecraft:kebab");
        painting(server, x + 2, y + 3, z + 10, "minecraft:aztec");
        painting(server, x + 4, y + 2, z + 10, "minecraft:bust");
    }

    private static void frame(TestServerContext server, String type, int x, int y, int z, String item, int rotation) {
        server.runCommand(String.format(Locale.ROOT, "summon %s %d %d %d {Facing:2b,ItemRotation:%db,Item:{id:\"%s\",count:1}}",
                type, x, y, z, rotation, item));
    }

    private static void painting(TestServerContext server, int x, int y, int z, String variant) {
        server.runCommand(String.format(Locale.ROOT, "summon painting %d %d %d {facing:2b,variant:\"%s\"}", x, y, z, variant));
    }

    private static String fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        return String.format(Locale.ROOT, "fill %d %d %d %d %d %d %s", x1, y1, z1, x2, y2, z2, block);
    }

    private static String setblock(int x, int y, int z, String block) {
        return String.format(Locale.ROOT, "setblock %d %d %d %s", x, y, z, block);
    }
}
