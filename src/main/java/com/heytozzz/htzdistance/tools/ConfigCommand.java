package com.heytozzz.htzdistance.tools;

import com.heytozzz.htzdistance.config.ConfigManager;
import com.heytozzz.htzdistance.config.HtzConfig;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
//? if >=26.1 {
/*import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;
*///?} else {
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
//?}

/**
 * {@code /htzdistance config}: every config file option, by its JSON name.
 * <ul>
 *     <li>{@code config}: lists all options and their values</li>
 *     <li>{@code config <option>}: shows one option</li>
 *     <li>{@code config <option> <value>}: changes and saves it (out-of-range values are clamped)</li>
 *     <li>{@code config reset}: restores the defaults</li>
 * </ul>
 * Per-type options are written as {@code <type>.<option>}, e.g. {@code itemFrame.threshold}.
 * Options are found by reflection, so new config fields show up here automatically.
 */
final class ConfigCommand {
    /** Internal bookkeeping fields that are not user options. */
    private static final List<String> HIDDEN = List.of("hologramsDebugEntryEnabled");

    /** One option: the field path from {@link HtzConfig} to the value. */
    private record Option(String name, Field[] path) {
        /** Colors (tintColor, glowColor) are ints written as #RRGGBB. */
        boolean isColor() {
            return name.endsWith("Color") && leaf().getType() == int.class;
        }

        Field leaf() {
            return path[path.length - 1];
        }

        Object holder(HtzConfig config) throws IllegalAccessException {
            Object holder = config;
            for (int i = 0; i < path.length - 1; i++) {
                holder = path[i].get(holder);
            }
            return holder;
        }

        Object get(HtzConfig config) throws IllegalAccessException {
            return leaf().get(holder(config));
        }

        void set(HtzConfig config, Object value) throws IllegalAccessException {
            leaf().set(holder(config), value);
        }

        String format(HtzConfig config) throws IllegalAccessException {
            Object value = get(config);
            if (isColor()) {
                return String.format(Locale.ROOT, "#%06X", (Integer) value);
            }
            if (value instanceof Enum<?> e) {
                return e.name().toLowerCase(Locale.ROOT);
            }
            return String.valueOf(value);
        }
    }

    private static final List<Option> OPTIONS = findOptions();

    private ConfigCommand() {
    }

    static LiteralArgumentBuilder<FabricClientCommandSource> build() {
        LiteralArgumentBuilder<FabricClientCommandSource> root = literal("config");
        root.executes(ConfigCommand::list);
        root.then(literal("reset").executes(ConfigCommand::reset));
        for (Option option : OPTIONS) {
            root.then(literal(option.name())
                    .executes(context -> show(context, option))
                    .then(valueArgument(option)));
        }
        return root;
    }

    private static RequiredArgumentBuilder<FabricClientCommandSource, ?> valueArgument(Option option) {
        Class<?> type = option.leaf().getType();
        if (option.isColor()) {
            // Greedy: "#" is not allowed in a single-word argument.
            return argument("value", StringArgumentType.greedyString())
                    .executes(context -> set(context, option, parseColor(StringArgumentType.getString(context, "value").trim())));
        }
        if (type == boolean.class) {
            return argument("value", BoolArgumentType.bool())
                    .executes(context -> set(context, option, BoolArgumentType.getBool(context, "value")));
        }
        if (type == int.class) {
            return argument("value", IntegerArgumentType.integer())
                    .executes(context -> set(context, option, IntegerArgumentType.getInteger(context, "value")));
        }
        if (type == double.class) {
            return argument("value", DoubleArgumentType.doubleArg())
                    .executes(context -> set(context, option, DoubleArgumentType.getDouble(context, "value")));
        }
        if (type == float.class) {
            return argument("value", FloatArgumentType.floatArg())
                    .executes(context -> set(context, option, FloatArgumentType.getFloat(context, "value")));
        }
        if (type.isEnum()) {
            List<String> names = Arrays.stream(type.getEnumConstants())
                    .map(constant -> ((Enum<?>) constant).name().toLowerCase(Locale.ROOT))
                    .toList();
            ArgumentType<String> word = StringArgumentType.word();
            return argument("value", word)
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(names, builder))
                    .executes(context -> set(context, option, parseEnum(type, StringArgumentType.getString(context, "value"))));
        }
        throw new IllegalStateException("Unsupported config type " + type + " for " + option.name());
    }

    private static int list(CommandContext<FabricClientCommandSource> context) {
        HtzConfig config = ConfigManager.get();
        context.getSource().sendFeedback(Component.translatable("htzdistance.message.config.header"));
        for (Option option : OPTIONS) {
            context.getSource().sendFeedback(Component.literal(" " + option.name() + " = " + value(option, config)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int show(CommandContext<FabricClientCommandSource> context, Option option) {
        context.getSource().sendFeedback(Component.translatable("htzdistance.message.config.value",
                option.name(), value(option, ConfigManager.get())));
        return Command.SINGLE_SUCCESS;
    }

    private static int set(CommandContext<FabricClientCommandSource> context, Option option, Object value) {
        if (value == null) {
            context.getSource().sendError(Component.translatable("htzdistance.message.config.invalid", option.name()));
            return 0;
        }
        HtzConfig copy = ConfigManager.copy();
        try {
            option.set(copy, value);
        } catch (IllegalAccessException e) {
            context.getSource().sendError(Component.translatable("htzdistance.message.config.invalid", option.name()));
            return 0;
        }
        ConfigManager.apply(copy);
        // apply() clamps out-of-range values: report what was actually stored.
        context.getSource().sendFeedback(Component.translatable("htzdistance.message.config.set",
                option.name(), value(option, ConfigManager.get())));
        return Command.SINGLE_SUCCESS;
    }

    private static int reset(CommandContext<FabricClientCommandSource> context) {
        HtzConfig defaults = new HtzConfig();
        defaults.hologramsDebugEntryEnabled = ConfigManager.get().hologramsDebugEntryEnabled;
        ConfigManager.apply(defaults);
        context.getSource().sendFeedback(Component.translatable("htzdistance.message.config.reset"));
        return Command.SINGLE_SUCCESS;
    }

    private static String value(Option option, HtzConfig config) {
        try {
            return option.format(config);
        } catch (IllegalAccessException e) {
            return "?";
        }
    }

    private static Integer parseColor(String text) {
        String hex = text.startsWith("#") ? text.substring(1) : text;
        if (!hex.matches("[0-9a-fA-F]{6}")) {
            return null;
        }
        return Integer.parseInt(hex, 16);
    }

    private static Object parseEnum(Class<?> type, String text) {
        for (Object constant : type.getEnumConstants()) {
            if (((Enum<?>) constant).name().equalsIgnoreCase(text)) {
                return constant;
            }
        }
        return null;
    }

    private static List<Option> findOptions() {
        List<Option> options = new ArrayList<>();
        for (Field field : HtzConfig.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) || HIDDEN.contains(field.getName())) {
                continue;
            }
            if (field.getType() == HtzConfig.KindSettings.class) {
                for (Field inner : HtzConfig.KindSettings.class.getFields()) {
                    if (!Modifier.isStatic(inner.getModifiers())) {
                        options.add(new Option(field.getName() + "." + inner.getName(), new Field[]{field, inner}));
                    }
                }
            } else {
                options.add(new Option(field.getName(), new Field[]{field}));
            }
        }
        return List.copyOf(options);
    }
}
