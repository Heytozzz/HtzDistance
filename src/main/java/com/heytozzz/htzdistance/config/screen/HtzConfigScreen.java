package com.heytozzz.htzdistance.config.screen;

import com.heytozzz.htzdistance.capture.RangeLearner;
import com.heytozzz.htzdistance.config.ConfigManager;
import com.heytozzz.htzdistance.config.HtzConfig;
import com.heytozzz.htzdistance.ghost.GhostKind;
import com.heytozzz.htzdistance.ghost.GhostManager;
import com.heytozzz.htzdistance.ghost.GhostStore;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Config screen built with YACL. Only loaded when YACL is installed (see {@code ModMenuIntegration}).
 *
 * <p>Edits a copy of the config; the copy replaces the live config when the user saves.
 */
public final class HtzConfigScreen {
    private static final String KEY = "htzdistance.config.";

    private HtzConfigScreen() {
    }

    public static Screen create(Screen parent) {
        HtzConfig defaults = new HtzConfig();
        HtzConfig edited = ConfigManager.copy();

        ConfigCategory.Builder general = ConfigCategory.createBuilder()
                .name(text("category.general"))
                .option(bool("enabled", defaults.enabled, () -> edited.enabled, v -> edited.enabled = v));

        for (GhostKind kind : GhostKind.values()) {
            HtzConfig.KindSettings settings = edited.settingsFor(kind);
            HtzConfig.KindSettings defaultSettings = defaults.settingsFor(kind);
            general.group(OptionGroup.createBuilder()
                    .name(text("kind." + kind.configKey()))
                    .description(OptionDescription.of(text("kind.description")))
                    .option(bool("kind.enabled", defaultSettings.enabled, () -> settings.enabled, v -> settings.enabled = v))
                    .option(Option.<Double>createBuilder()
                            .name(text("kind.threshold"))
                            .description(OptionDescription.of(text("kind.threshold.description")))
                            .binding(defaultSettings.threshold, () -> settings.threshold, v -> settings.threshold = v)
                            .controller(opt -> DoubleSliderControllerBuilder.create(opt).range(1.0, 128.0).step(0.5))
                            .build())
                    .build());
        }

        general.group(OptionGroup.createBuilder()
                .name(text("group.detection"))
                .option(Option.<Double>createBuilder()
                        .name(text("thresholdMargin"))
                        .description(OptionDescription.of(text("thresholdMargin.description")))
                        .binding(defaults.thresholdMargin, () -> edited.thresholdMargin, v -> edited.thresholdMargin = v)
                        .controller(opt -> DoubleSliderControllerBuilder.create(opt).range(0.0, 8.0).step(0.25))
                        .build())
                .option(Option.<Double>createBuilder()
                        .name(text("inRangeTimeoutSeconds"))
                        .description(OptionDescription.of(text("inRangeTimeoutSeconds.description")))
                        .binding(defaults.inRangeTimeoutSeconds, () -> edited.inRangeTimeoutSeconds, v -> edited.inRangeTimeoutSeconds = v)
                        .controller(opt -> DoubleSliderControllerBuilder.create(opt).range(0.5, 60.0).step(0.5))
                        .build())
                .option(Option.<Double>createBuilder()
                        .name(text("reconcileRadius"))
                        .description(OptionDescription.of(text("reconcileRadius.description")))
                        .binding(defaults.reconcileRadius, () -> edited.reconcileRadius, v -> edited.reconcileRadius = v)
                        .controller(opt -> DoubleSliderControllerBuilder.create(opt).range(0.1, 2.0).step(0.05))
                        .build())
                .option(bool("learningMode", defaults.learningMode, () -> edited.learningMode, v -> edited.learningMode = v))
                .build());
        general.group(learnedGroup());

        ConfigCategory.Builder rendering = ConfigCategory.createBuilder()
                .name(text("category.rendering"))
                .option(Option.<Double>createBuilder()
                        .name(text("maxRenderDistance"))
                        .description(OptionDescription.of(text("maxRenderDistance.description")))
                        .binding(defaults.maxRenderDistance, () -> edited.maxRenderDistance, v -> edited.maxRenderDistance = v)
                        .controller(opt -> DoubleSliderControllerBuilder.create(opt).range(8.0, 512.0).step(8.0))
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(text("maxGhosts"))
                        .description(OptionDescription.of(text("maxGhosts.description")))
                        .binding(defaults.maxGhosts, () -> edited.maxGhosts, v -> edited.maxGhosts = v)
                        .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(16, 8192).step(16))
                        .build())
                .option(bool("occlusionCulling", defaults.occlusionCulling, () -> edited.occlusionCulling, v -> edited.occlusionCulling = v))
                .option(Option.<HtzConfig.GhostStyle>createBuilder()
                        .name(text("ghostStyle"))
                        .description(OptionDescription.of(text("ghostStyle.description")))
                        .binding(defaults.ghostStyle, () -> edited.ghostStyle, v -> edited.ghostStyle = v)
                        .controller(opt -> EnumControllerBuilder.create(opt)
                                .enumClass(HtzConfig.GhostStyle.class)
                                .formatValue(style -> text("ghostStyle." + style.name().toLowerCase(Locale.ROOT))))
                        .build())
                .option(Option.<Color>createBuilder()
                        .name(text("tintColor"))
                        .description(OptionDescription.of(text("tintColor.description")))
                        .binding(new Color(defaults.tintColor), () -> new Color(edited.tintColor), v -> edited.tintColor = v.getRGB() & 0xFFFFFF)
                        .controller(ColorControllerBuilder::create)
                        .build())
                .option(Option.<Float>createBuilder()
                        .name(text("translucentAlpha"))
                        .description(OptionDescription.of(text("translucentAlpha.description")))
                        .binding(defaults.translucentAlpha, () -> edited.translucentAlpha, v -> edited.translucentAlpha = v)
                        .controller(opt -> FloatSliderControllerBuilder.create(opt).range(0.1f, 1.0f).step(0.05f))
                        .build());

        ConfigCategory.Builder storage = ConfigCategory.createBuilder()
                .name(text("category.storage"))
                .option(bool("persistence", defaults.persistence, () -> edited.persistence, v -> edited.persistence = v))
                .option(bool("debugLogging", defaults.debugLogging, () -> edited.debugLogging, v -> edited.debugLogging = v));

        return YetAnotherConfigLib.createBuilder()
                .title(text("title"))
                .category(general.build())
                .category(rendering.build())
                .category(storage.build())
                .save(() -> ConfigManager.apply(edited))
                .build()
                .generateScreen(parent);
    }

    /** Read-only view of the ranges learned on the current server, plus a reset button. */
    private static OptionGroup learnedGroup() {
        GhostStore store = GhostManager.get().store();
        String server = store == null ? null : store.key().server();
        OptionGroup.Builder group = OptionGroup.createBuilder()
                .name(text("group.learned"))
                .description(OptionDescription.of(text("group.learned.description")));
        if (server == null) {
            return group.option(LabelOption.create(text("learned.noServer"))).build();
        }
        for (GhostKind kind : GhostKind.values()) {
            OptionalDouble estimate = RangeLearner.get().estimate(server, kind);
            int[] counts = RangeLearner.get().sampleCounts(server, kind);
            Component value = estimate.isPresent()
                    ? Component.translatable(KEY + "learned.value", String.format(Locale.ROOT, "%.2f", estimate.getAsDouble()), counts[0], counts[1])
                    : Component.translatable(KEY + "learned.notEnough", counts[0], counts[1], RangeLearner.MIN_SAMPLES);
            group.option(LabelOption.create(Component.empty().append(text("kind." + kind.configKey())).append(": ").append(value)));
        }
        group.option(ButtonOption.createBuilder()
                .name(text("learned.reset"))
                .description(OptionDescription.of(text("learned.reset.description")))
                .action(screen -> RangeLearner.get().reset(server))
                .build());
        return group.build();
    }

    private static Option<Boolean> bool(String key, boolean def, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return Option.<Boolean>createBuilder()
                .name(text(key))
                .description(OptionDescription.of(text(key + ".description")))
                .binding(def, getter, setter)
                .controller(TickBoxControllerBuilder::create)
                .build();
    }

    private static Component text(String key) {
        return Component.translatable(KEY + key);
    }
}
