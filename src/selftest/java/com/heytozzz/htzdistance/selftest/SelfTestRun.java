package com.heytozzz.htzdistance.selftest;

import com.heytozzz.htzdistance.GhostStats;
import com.heytozzz.htzdistance.HtzDistanceClient;
import com.heytozzz.htzdistance.config.ConfigManager;
import com.heytozzz.htzdistance.config.HtzConfig;
import com.heytozzz.htzdistance.ghost.Ghost;
import com.heytozzz.htzdistance.ghost.GhostKind;
import com.heytozzz.htzdistance.ghost.GhostManager;
import com.heytozzz.htzdistance.ghost.GhostStore;
import com.heytozzz.htzdistance.render.GhostEntityFactory;
import com.heytozzz.htzdistance.render.GhostRenderer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
//? if <26.1 {
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.flag.FeatureFlags;
//?}

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Predicate;

/**
 * The self-test script. Driven by the client tick; every step either finishes at once or waits.
 *
 * <p>Layout (relative to the spawn surface {@code (bx, y, bz)}):
 * <ul>
 *   <li>rotation row at z = bz + 12: eight armor stands (yaw 0..315 every 45 degrees, carved
 *       pumpkin heads), eight item frames with arrows in the eight item rotations, and a pillar
 *       with a frame on each side;</li>
 *   <li>armory at x = bx + 30..68, z = bz + 30..68: 400 armor stands with full gear and random
 *       45-degree yaws;</li>
 *   <li>warehouse at z = bz + 80..104: four double-sided shelves of item frames (2048 frames,
 *       one glow frame with a map every 16).</li>
 * </ul>
 */
final class SelfTestRun {
    private static final String WORLD = "htzdistance-selftest";
    private static final int MEASURE_TICKS = 200;
    private static final String[] ITEMS = {
            "iron_sword", "diamond_sword", "netherite_sword", "bow", "crossbow", "shield", "iron_pickaxe",
            "diamond_axe", "golden_apple", "apple", "bread", "cooked_beef", "emerald", "diamond",
            "iron_ingot", "gold_ingot", "redstone", "lapis_lazuli", "coal", "oak_log", "stone",
            "cobblestone", "torch", "lantern", "chest", "barrel", "arrow", "trident", "totem_of_undying",
            "ender_pearl", "book", "compass", "clock", "name_tag", "saddle", "spyglass"
    };
    private static final String[][] ARMOR = {
            {"diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots", "diamond_sword"},
            {"netherite_helmet", "netherite_chestplate", "netherite_leggings", "netherite_boots", "netherite_axe"},
            {"iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots", "iron_sword"},
            {"golden_helmet", "golden_chestplate", "golden_leggings", "golden_boots", "golden_sword"},
            {"chainmail_helmet", "chainmail_chestplate", "chainmail_leggings", "chainmail_boots", "trident"}
    };

    private final Path gameDir;
    private final Deque<Step> steps = new ArrayDeque<>();
    private final StringBuilder report = new StringBuilder();
    private final List<String> failures = new ArrayList<>();
    private boolean started;
    private boolean finished;
    private int waitTicks;

    private int bx;
    private int y;
    private int bz;
    private int expectedStands;
    private int expectedFrames;
    private int nearMaps;

    // Options restored at the end.
    private int savedRenderDistance;
    private int savedFramerate;
    private boolean savedVsync;
    private int savedFov;
    private double savedEntityDistance;

    /** Real entities' rendered yaw / frame state, keyed by rounded position. */
    private final java.util.Map<String, Float> realBodyYaw = new java.util.HashMap<>();
    private final java.util.Map<String, String> realFrameState = new java.util.HashMap<>();

    private interface Step {
        /** @return true when the step is complete */
        boolean run(Minecraft minecraft) throws Exception;
    }

    SelfTestRun(Path gameDir) {
        this.gameDir = gameDir;
        buildScript();
    }

    void tick(Minecraft minecraft) {
        if (finished) {
            return;
        }
        if (!started) {
            if (!(screen(minecraft) instanceof TitleScreen)) {
                return;
            }
            started = true;
            line("# HtzDistance self-test");
            line("Minecraft " + HtzDistanceClient.MINECRAFT + ", HtzDistance " + HtzDistanceClient.VERSION
                    + ", " + FabricLoader.getInstance().getAllMods().size() + " mods loaded");
            line("Notable mods: " + notableMods());
        }
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        try {
            while (!steps.isEmpty()) {
                Step step = steps.peekFirst();
                if (!step.run(minecraft)) {
                    return;
                }
                steps.removeFirst();
                if (waitTicks > 0) {
                    return;
                }
            }
        } catch (Throwable t) {
            SelfTestClient.LOGGER.error("Self-test failed", t);
            failures.add("Exception: " + t);
            finish(minecraft);
        }
    }

    // ------------------------------------------------------------------ script

    private void buildScript() {
        steps.add(mc -> {
            savedRenderDistance = mc.options.renderDistance().get();
            savedFramerate = mc.options.framerateLimit().get();
            savedVsync = mc.options.enableVsync().get();
            savedFov = mc.options.fov().get();
            savedEntityDistance = mc.options.entityDistanceScaling().get();
            mc.options.entityDistanceScaling().set(1.0);
            mc.options.renderDistance().set(12);
            mc.options.framerateLimit().set(260);
            mc.options.enableVsync().set(false);

            HtzConfig config = new HtzConfig();
            config.maxGhosts = 8192;
            config.maxRenderDistance = 160.0;
            ConfigManager.apply(config);
            SelfTestState.serverLikeRanges = false;

            deleteWorldFolder();
            createWorld(mc);
            return true;
        });
        steps.add(waitUntil(mc -> mc.level != null && mc.player != null && mc.getSingleplayerServer() != null, 1200));
        steps.add(waitTicks(100));
        steps.add(this::buildScene);
        steps.add(waitTicks(100));

        // Phase 1: real entities (vanilla ranges), overview.
        steps.add(mc -> {
            overview(mc);
            return true;
        });
        steps.add(waitTicks(100));
        steps.add(mc -> {
            int stands = countReal(mc, GhostKind.ARMOR_STAND);
            int frames = countReal(mc, GhostKind.ITEM_FRAME) + countReal(mc, GhostKind.GLOW_ITEM_FRAME);
            line("");
            line(String.format(Locale.ROOT, "Spawned %d armor stands and %d item frames; client sees %d stands and %d frames",
                    expectedStands, expectedFrames, stands, frames));
            check(stands == expectedStands && frames == expectedFrames, "all spawned entities reach the client");
            recordRealRotations(mc);
            return true;
        });
        steps.add(rotationView());
        steps.add(waitTicks(60));
        steps.add(screenshot("real-rotations"));
        steps.add(mc -> {
            overview(mc);
            return true;
        });
        steps.add(waitTicks(60));
        steps.addAll(measure("Real entities (vanilla ranges)"));
        steps.add(screenshot("real-overview"));

        // Phase 2: server-like ranges -> everything becomes a ghost.
        steps.add(mc -> {
            GhostStats.CAPTURE_NANOS.set(0);
            GhostStats.CAPTURES.set(0);
            SelfTestState.serverLikeRanges = true;
            // Nudge the tracker: a section change re-evaluates every tracked entity.
            command(mc, "execute as @a at @s run tp @s ~ ~ ~-20");
            return true;
        });
        steps.add(waitTicks(60));
        steps.add(mc -> {
            overview(mc);
            return true;
        });
        steps.add(waitTicks(100));
        steps.add(mc -> {
            int total = expectedStands + expectedFrames;
            int ghosts = ghostCount();
            long captures = GhostStats.CAPTURES.get();
            double captureMs = GhostStats.CAPTURE_NANOS.get() / 1_000_000.0;
            line("");
            line(String.format(Locale.ROOT, "Captured %d ghosts in %.1f ms total (%.3f ms per entity)",
                    captures, captureMs, captures == 0 ? 0 : captureMs / captures));
            line(String.format(Locale.ROOT, "Ghost data size: %s", dataSize()));
            check(capturedMaps() >= nearMaps, "maps of the near map wall captured (" + capturedMaps() + " >= " + nearMaps + ")");
            check(ghosts == total, "every entity became a ghost (" + ghosts + "/" + total + ")");
            check(countReal(mc, null) == 0, "no real stands/frames left on the client");
            return true;
        });
        steps.addAll(measure("Ghosts, occlusion culling on"));
        steps.add(screenshot("ghost-overview"));
        steps.add(mc -> {
            updateConfig(c -> c.occlusionCulling = false);
            return true;
        });
        steps.add(waitTicks(20));
        steps.addAll(measure("Ghosts, occlusion culling off"));
        steps.add(mc -> {
            updateConfig(c -> c.occlusionCulling = true);
            return true;
        });

        // Rotation check on ghosts.
        steps.add(rotationView());
        steps.add(waitTicks(60));
        steps.add(screenshot("ghost-rotations"));
        steps.add(this::checkGhostRotations);

        // Phase 3: baseline without the mod drawing anything.
        steps.add(mc -> {
            overview(mc);
            updateConfig(c -> c.enabled = false);
            return true;
        });
        steps.add(waitTicks(60));
        steps.addAll(measure("Baseline: no entities, mod disabled"));
        steps.add(mc -> {
            finish(mc);
            return true;
        });
    }

    private Step waitTicks(int ticks) {
        return mc -> {
            waitTicks = ticks;
            return true;
        };
    }

    private Step waitUntil(Predicate<Minecraft> condition, int timeoutTicks) {
        int[] waited = {0};
        return mc -> {
            if (condition.test(mc)) {
                return true;
            }
            if (++waited[0] > timeoutTicks) {
                throw new IllegalStateException("Timed out waiting");
            }
            return false;
        };
    }

    //? if >=26.1 {
    /*private static net.minecraft.client.gui.screens.Screen screen(Minecraft mc) {
        return mc.gui.screen();
    }

    /^* Closes any screen another mod opened (welcome dialogs etc.) so it does not cover the world. ^/
    private static void closeScreen(Minecraft mc) {
        if (mc.gui.screen() != null) {
            mc.gui.setScreen(null);
        }
    }
    *///?} else {
    private static net.minecraft.client.gui.screens.Screen screen(Minecraft mc) {
        return mc.screen;
    }

    /** Closes any screen another mod opened (welcome dialogs etc.) so it does not cover the world. */
    private static void closeScreen(Minecraft mc) {
        if (mc.screen != null) {
            mc.setScreen(null);
        }
    }
    //?}

    // ------------------------------------------------------------------ world

    private void deleteWorldFolder() throws IOException {
        Path dir = gameDir.resolve("saves").resolve(WORLD);
        if (Files.exists(dir)) {
            try (var paths = Files.walk(dir)) {
                for (Path p : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.delete(p);
                }
            }
        }
    }

    private void createWorld(Minecraft mc) {
        //? if >=26.1 {
        /*LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        *///?} else {
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                new GameRules(FeatureFlags.DEFAULT_FLAGS), WorldDataConfiguration.DEFAULT);
        //?}
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(1L, false, false),
                registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                screen(mc));
    }

    private boolean buildScene(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            BlockPos player = server.getPlayerList().getPlayers().getFirst().blockPosition();
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, player);
            bx = surface.getX();
            y = surface.getY();
            bz = surface.getZ();
            run(server, "gamerule doDaylightCycle false");
            run(server, "gamerule advance_time false");
            run(server, "time set noon");
            run(server, "weather clear");
            run(server, "gamerule doMobSpawning false");
            run(server, "gamerule spawn_mobs false");

            Random random = new Random(42);
            int stands = 0;
            int frames = 0;

            // Rotation row.
            for (int i = 0; i < 8; i++) {
                run(server, String.format(Locale.ROOT,
                        "summon armor_stand %d.5 %d %d.5 {Rotation:[%df,0f],ShowArms:1b,NoBasePlate:1b,"
                                + "equipment:{head:{id:\"minecraft:carved_pumpkin\"},mainhand:{id:\"minecraft:diamond_sword\"},"
                                + "offhand:{id:\"minecraft:shield\"}}}",
                        bx - 14 + i * 4, y, bz + 12, i * 45));
                stands++;
            }
            run(server, String.format(Locale.ROOT, "fill %d %d %d %d %d %d stone", bx - 14, y + 1, bz + 21, bx + 16, y + 2, bz + 21));
            for (int i = 0; i < 8; i++) {
                spawnFrame(level, new BlockPos(bx - 14 + i * 4, y + 1, bz + 20), Direction.NORTH, item("arrow"), i, false);
                frames++;
            }
            // Map wall close to the player (map data is only synced inside the simulation distance).
            int mapIndex = 0;
            for (int i = 0; i < 16; i++) {
                spawnFrame(level, new BlockPos(bx - 14 + i * 2, y + 2, bz + 20), Direction.NORTH, map(level, mapIndex++), 0, true);
                frames++;
                nearMaps++;
            }
            BlockPos pillar = new BlockPos(bx + 22, y + 1, bz + 12);
            run(server, String.format(Locale.ROOT, "setblock %d %d %d stone", pillar.getX(), pillar.getY(), pillar.getZ()));
            for (Direction side : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                spawnFrame(level, pillar.relative(side), side, item("arrow"), 1, false);
                frames++;
            }

            // Armory.
            for (int gx = 0; gx < 20; gx++) {
                for (int gz = 0; gz < 20; gz++) {
                    String[] set = ARMOR[random.nextInt(ARMOR.length)];
                    run(server, String.format(Locale.ROOT,
                            "summon armor_stand %d.5 %d %d.5 {Rotation:[%df,0f],ShowArms:1b,"
                                    + "equipment:{head:{id:\"minecraft:%s\"},chest:{id:\"minecraft:%s\"},legs:{id:\"minecraft:%s\"},"
                                    + "feet:{id:\"minecraft:%s\"},mainhand:{id:\"minecraft:%s\"},offhand:{id:\"minecraft:shield\"}},"
                                    + "Pose:{RightArm:[-%df,0f,0f]}}",
                            bx + 30 + gx * 2, y, bz + 30 + gz * 2, random.nextInt(8) * 45,
                            set[0], set[1], set[2], set[3], set[4], random.nextInt(90)));
                    stands++;
                }
            }

            // Warehouse: four double-sided shelves, 64 long and 4 high.
            for (int shelf = 0; shelf < 4; shelf++) {
                int sz = bz + 80 + shelf * 8;
                run(server, String.format(Locale.ROOT, "fill %d %d %d %d %d %d oak_planks", bx - 32, y + 1, sz, bx + 31, y + 4, sz));
                for (int sx = 0; sx < 64; sx++) {
                    for (int sy = 0; sy < 4; sy++) {
                        for (Direction side : new Direction[]{Direction.NORTH, Direction.SOUTH}) {
                            BlockPos pos = new BlockPos(bx - 32 + sx, y + 1 + sy, sz).relative(side);
                            boolean glow = (frames % 16) == 0;
                            ItemStack stack = glow ? map(level, mapIndex++) : item(ITEMS[random.nextInt(ITEMS.length)]);
                            spawnFrame(level, pos, side, stack, random.nextInt(8), glow);
                            frames++;
                        }
                    }
                }
            }
            expectedStands = stands;
            expectedFrames = frames;
        });
        return true;
    }

    private static void spawnFrame(ServerLevel level, BlockPos pos, Direction facing, ItemStack stack, int rotation, boolean glow) {
        ItemFrame frame = glow ? new GlowItemFrame(level, pos, facing) : new ItemFrame(level, pos, facing);
        frame.setItem(stack);
        frame.setRotation(rotation);
        level.addFreshEntity(frame);
    }

    private static ItemStack item(String name) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(name)));
    }

    private ItemStack map(ServerLevel level, int index) {
        ItemStack stack = MapItem.create(level, index * 128, 0, (byte) 0, false, false);
        MapItemSavedData data = MapItem.getSavedData(stack, level);
        if (data != null) {
            Random random = new Random(index);
            int stripe = 4 + random.nextInt(12);
            byte a = (byte) (random.nextInt(60) * 4 + 2);
            byte b = (byte) (random.nextInt(60) * 4 + 1);
            for (int px = 0; px < 128; px++) {
                for (int py = 0; py < 128; py++) {
                    data.setColor(px, py, ((px / stripe + py / stripe) % 2 == 0) ? a : b);
                }
            }
        }
        return stack;
    }

    private static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private void command(Minecraft mc, String command) {
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> run(server, command.replace("@p", "@a")));
    }

    /** Elevated point south of the scene looking north-east over everything. */
    private void overview(Minecraft mc) {
        closeScreen(mc);
        command(mc, String.format(Locale.ROOT, "tp @p %d.5 %d %d.5 -25 25", bx + 4, y + 18, bz - 14));
    }

    /** Point in front of the rotation row, beyond 16 blocks from the stands, looking south. */
    private Step rotationView() {
        return mc -> {
            closeScreen(mc);
            command(mc, String.format(Locale.ROOT, "tp @p %d.5 %d %d.5 0 8", bx + 1, y + 3, bz - 6));
            mc.options.fov().set(50);
            return true;
        };
    }

    // ------------------------------------------------------------------ checks

    private int countReal(Minecraft mc, GhostKind only) {
        ClientLevel level = mc.level;
        int count = 0;
        for (Entity entity : level.entitiesForRendering()) {
            GhostKind kind = com.heytozzz.htzdistance.capture.SnapshotFactory.kindOf(entity);
            if (kind != null && (only == null || kind == only)) {
                count++;
            }
        }
        return count;
    }

    private static int ghostCount() {
        GhostStore store = GhostManager.get().store();
        return store == null ? 0 : store.size();
    }

    private static String key(double x, double y, double z) {
        return Math.round(x * 2) + "," + Math.round(y * 2) + "," + Math.round(z * 2);
    }

    /** Remembers what vanilla renders for the rotation-row entities (body yaw, frame facing/rotation). */
    private void recordRealRotations(Minecraft mc) {
        AABB row = new AABB(bx - 16, y - 1, bz + 10, bx + 25, y + 4, bz + 22);
        for (Entity entity : mc.level.getEntities((Entity) null, row, e -> true)) {
            if (entity instanceof ItemFrame frame) {
                realFrameState.put(key(frame.getX(), frame.getY(), frame.getZ()), frame.getDirection() + "/" + frame.getRotation());
            } else if (com.heytozzz.htzdistance.capture.SnapshotFactory.kindOf(entity) == GhostKind.ARMOR_STAND) {
                EntityRenderState state = mc.getEntityRenderDispatcher().extractEntity(entity, 1.0f);
                if (state instanceof LivingEntityRenderState living) {
                    realBodyYaw.put(key(entity.getX(), entity.getY(), entity.getZ()), living.bodyRot);
                }
            }
        }
        line(String.format(Locale.ROOT, "Recorded %d real armor stand yaws and %d real frames in the rotation row",
                realBodyYaw.size(), realFrameState.size()));
    }

    private boolean checkGhostRotations(Minecraft mc) {
        GhostStore store = GhostManager.get().store();
        line("");
        line("Rotation check (ghost vs real, rendered values):");
        int standsChecked = 0;
        int framesChecked = 0;
        for (Ghost ghost : store.view()) {
            String k = key(ghost.snapshot().x(), ghost.snapshot().y(), ghost.snapshot().z());
            Entity entity = GhostEntityFactory.getOrCreate(ghost, mc.level);
            if (entity == null) {
                continue;
            }
            if (ghost.kind() == GhostKind.ARMOR_STAND && realBodyYaw.containsKey(k)) {
                float real = realBodyYaw.get(k);
                EntityRenderState state = mc.getEntityRenderDispatcher().extractEntity(entity, 1.0f);
                float ghostYaw = state instanceof LivingEntityRenderState living ? living.bodyRot : Float.NaN;
                boolean ok = Math.abs(Mth.wrapDegrees(ghostYaw - real)) < 0.5f;
                line(String.format(Locale.ROOT, "  armor stand at %s: real %.1f, ghost %.1f %s", k, Mth.wrapDegrees(real),
                        Mth.wrapDegrees(ghostYaw), ok ? "OK" : "MISMATCH"));
                check(ok, "armor stand yaw " + Mth.wrapDegrees(real) + " kept by the ghost");
                standsChecked++;
            } else if (entity instanceof ItemFrame frame && realFrameState.containsKey(k)) {
                String real = realFrameState.get(k);
                String ghostState = frame.getDirection() + "/" + frame.getRotation();
                boolean ok = real.equals(ghostState);
                line(String.format(Locale.ROOT, "  item frame at %s: real %s, ghost %s %s", k, real, ghostState, ok ? "OK" : "MISMATCH"));
                check(ok, "item frame " + real + " kept by the ghost");
                framesChecked++;
            }
        }
        check(standsChecked == 8, "all 8 rotation-row armor stands checked (" + standsChecked + ")");
        check(framesChecked == 12 + nearMaps, "all " + (12 + nearMaps) + " rotation-row item frames checked (" + framesChecked + ")");
        return true;
    }

    private static int capturedMaps() {
        GhostStore store = GhostManager.get().store();
        int maps = 0;
        for (Ghost ghost : store.view()) {
            if (ghost.snapshot().map() != null) {
                maps++;
            }
        }
        return maps;
    }

    private String dataSize() {
        GhostStore store = GhostManager.get().store();
        long entityBytes = 0;
        long mapBytes = 0;
        int maps = 0;
        for (Ghost ghost : store.view()) {
            entityBytes += nbtSize(ghost.snapshot().entityTag());
            if (ghost.snapshot().map() != null) {
                mapBytes += ghost.snapshot().map().colors().length;
                maps++;
            }
        }
        return String.format(Locale.ROOT, "%.1f KiB entity NBT + %.1f KiB map colors (%d maps)",
                entityBytes / 1024.0, mapBytes / 1024.0, maps);
    }

    private static long nbtSize(CompoundTag tag) {
        long[] count = {0};
        try (DataOutputStream out = new DataOutputStream(new OutputStream() {
            @Override
            public void write(int b) {
                count[0]++;
            }

            @Override
            public void write(byte[] b, int off, int len) {
                count[0] += len;
            }
        })) {
            NbtIo.write(tag, out);
        } catch (IOException e) {
            return -1;
        }
        return count[0];
    }

    // ------------------------------------------------------------------ measuring

    private List<Step> measure(String name) {
        long[] start = new long[6];
        int[] fpsSum = {0};
        int[] fpsMin = {Integer.MAX_VALUE};
        int[] samples = {0};
        int[] ticks = {0};
        List<Step> list = new ArrayList<>();
        list.add(mc -> {
            closeScreen(mc);
            start[0] = GhostStats.RENDER_NANOS.get();
            start[1] = GhostStats.RENDER_FRAMES.get();
            start[2] = GhostStats.CULLING_NANOS.get();
            start[3] = GhostStats.CULLING_PASSES.get();
            start[4] = System.nanoTime();
            return true;
        });
        list.add(mc -> {
            ticks[0]++;
            if (ticks[0] % 20 == 0) {
                int fps = mc.getFps();
                fpsSum[0] += fps;
                fpsMin[0] = Math.min(fpsMin[0], fps);
                samples[0]++;
            }
            return ticks[0] >= MEASURE_TICKS;
        });
        list.add(mc -> {
            long frames = GhostStats.RENDER_FRAMES.get() - start[1];
            double renderMs = frames == 0 ? 0 : (GhostStats.RENDER_NANOS.get() - start[0]) / 1_000_000.0 / frames;
            long passes = GhostStats.CULLING_PASSES.get() - start[3];
            double cullMs = passes == 0 ? 0 : (GhostStats.CULLING_NANOS.get() - start[2]) / 1_000_000.0 / passes;
            double seconds = (System.nanoTime() - start[4]) / 1e9;
            line("");
            line("## " + name);
            line(String.format(Locale.ROOT, "  FPS avg %d, min %d (window active: %s)",
                    samples[0] == 0 ? 0 : fpsSum[0] / samples[0], fpsMin[0] == Integer.MAX_VALUE ? 0 : fpsMin[0], mc.isWindowActive()));
            line(String.format(Locale.ROOT, "  ghosts stored %d, drawn last frame %d", ghostCount(), GhostRenderer.lastFrameRendered()));
            line(String.format(Locale.ROOT, "  ghost selection+render cost: %.3f ms/frame over %d frames", renderMs, frames));
            line(String.format(Locale.ROOT, "  culling pass (worker thread): %.3f ms over %d passes in %.1f s", cullMs, passes, seconds));
            return true;
        });
        return list;
    }

    private Step screenshot(String name) {
        return mc -> {
            closeScreen(mc);
            RenderTarget target;
            //? if >=26.1 {
            /*target = mc.gameRenderer.mainRenderTarget();
            *///?} else {
            target = mc.getMainRenderTarget();
            //?}
            Screenshot.grab(gameDir.toFile(), "htzdistance-selftest-" + name + ".png", target, 1, message -> { });
            line("Screenshot: screenshots/htzdistance-selftest-" + name + ".png");
            return true;
        };
    }

    // ------------------------------------------------------------------ reporting

    private static void updateConfig(java.util.function.Consumer<HtzConfig> change) {
        HtzConfig config = ConfigManager.get();
        change.accept(config);
        ConfigManager.apply(config);
    }

    private void check(boolean ok, String what) {
        line((ok ? "PASS: " : "FAIL: ") + what);
        if (!ok) {
            failures.add(what);
        }
    }

    private void line(String text) {
        report.append(text).append('\n');
        SelfTestClient.LOGGER.info(text);
    }

    private static String notableMods() {
        List<String> found = new ArrayList<>();
        for (String id : new String[]{"sodium", "iris", "entityculling", "bobby", "entity_model_features",
                "entity_texture_features", "immediatelyfast", "moreculling", "lithium", "dynamic_fps"}) {
            FabricLoader.getInstance().getModContainer(id)
                    .ifPresent(m -> found.add(id + " " + m.getMetadata().getVersion().getFriendlyString()));
        }
        return String.join(", ", found);
    }

    private void finish(Minecraft mc) {
        if (finished) {
            return;
        }
        finished = true;
        line("");
        line(failures.isEmpty() ? "RESULT: ALL CHECKS PASSED" : "RESULT: " + failures.size() + " FAILURE(S): " + failures);
        try {
            Files.writeString(gameDir.resolve("htzdistance-selftest-report.txt"), report.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SelfTestClient.LOGGER.error("Could not write the self-test report", e);
        }
        mc.options.renderDistance().set(savedRenderDistance);
        mc.options.framerateLimit().set(savedFramerate);
        mc.options.enableVsync().set(savedVsync);
        mc.options.fov().set(savedFov);
        mc.options.entityDistanceScaling().set(savedEntityDistance);
        SelfTestState.serverLikeRanges = false;
        mc.stop();
    }
}
