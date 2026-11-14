package com.heytozzz.htzdistance.tools;

import com.heytozzz.htzdistance.ghost.GhostManager;
import com.heytozzz.htzdistance.ghost.GhostStore;
import com.heytozzz.htzdistance.persist.GhostPersistence;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
//? if >=26.1 {
/*import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;
*///?} else {
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
//?}

/**
 * Client-side {@code /htzdistance} command:
 * <ul>
 *     <li>{@code /htzdistance} or {@code stats}: ghost counts and costs</li>
 *     <li>{@code toggle}: pause the mod (ghosts hidden, no work at all) or resume it</li>
 *     <li>{@code config ...}: read and change the config (see {@link ConfigCommand})</li>
 *     <li>{@code clear}: forget the ghosts of the current dimension</li>
 *     <li>{@code clear <radius>}: forget the ghosts within that many blocks</li>
 *     <li>{@code clear all}: forget the ghosts of every dimension of this server, saved files included</li>
 * </ul>
 */
public final class HtzCommands {
    private HtzCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("htzdistance")
                .executes(HtzCommands::stats)
                .then(literal("stats").executes(HtzCommands::stats))
                .then(literal("toggle").executes(HtzCommands::toggle))
                .then(ConfigCommand.build())
                .then(literal("clear")
                        .executes(context -> clear(context, -1.0))
                        .then(literal("all").executes(HtzCommands::clearAll))
                        .then(argument("radius", DoubleArgumentType.doubleArg(1.0, 4096.0))
                                .executes(context -> clear(context, DoubleArgumentType.getDouble(context, "radius"))))));
    }

    private static int stats(CommandContext<FabricClientCommandSource> context) {
        for (String line : DebugStats.lines()) {
            context.getSource().sendFeedback(Component.literal(line));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int toggle(CommandContext<FabricClientCommandSource> context) {
        GhostToggle.toggle();
        context.getSource().sendFeedback(GhostToggle.stateMessage());
        return Command.SINGLE_SUCCESS;
    }

    /** Clears the current dimension, or only the ghosts within {@code radius} blocks if positive. */
    private static int clear(CommandContext<FabricClientCommandSource> context, double radius) {
        GhostStore store = GhostManager.get().store();
        if (store == null) {
            context.getSource().sendError(Component.translatable("htzdistance.message.noWorld"));
            return 0;
        }
        int removed;
        if (radius > 0.0) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                context.getSource().sendError(Component.translatable("htzdistance.message.noWorld"));
                return 0;
            }
            double px = player.getX();
            double py = player.getY();
            double pz = player.getZ();
            double radiusSqr = radius * radius;
            removed = store.removeIf(ghost -> ghost.snapshot().distanceSqr(px, py, pz) <= radiusSqr);
            context.getSource().sendFeedback(Component.translatable("htzdistance.message.clearedRadius",
                    removed, formatRadius(radius)));
        } else {
            removed = store.size();
            store.clear();
            context.getSource().sendFeedback(Component.translatable("htzdistance.message.cleared", removed));
        }
        if (removed > 0) {
            GhostPersistence.saveNow();
        }
        return Math.max(removed, 1);
    }

    private static int clearAll(CommandContext<FabricClientCommandSource> context) {
        GhostStore store = GhostManager.get().store();
        if (store == null) {
            context.getSource().sendError(Component.translatable("htzdistance.message.noWorld"));
            return 0;
        }
        int removed = store.size();
        store.clear();
        GhostPersistence.forgetServer(store.key().server());
        context.getSource().sendFeedback(Component.translatable("htzdistance.message.clearedAll", removed));
        return Math.max(removed, 1);
    }

    private static String formatRadius(double radius) {
        return radius == Math.rint(radius) ? Long.toString((long) radius) : Double.toString(radius);
    }
}
