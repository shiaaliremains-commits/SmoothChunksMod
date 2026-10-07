package my.smoothchunks.client

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import dev.isxander.yacl3.api.ConfigCategory
import dev.isxander.yacl3.api.Option
import dev.isxander.yacl3.api.OptionDescription
import dev.isxander.yacl3.api.OptionGroup
import dev.isxander.yacl3.api.YetAnotherConfigLib
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder
import my.smoothchunks.PreloadPayload
import my.smoothchunks.Smoothchunks
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class ModMenuIntegration : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> {
        return ConfigScreenFactory { parent: Screen -> createScreen(parent) }
    }

    companion object {
        var pregenRadius = 30

        fun createScreen(parent: Screen?): Screen {
            val antiStutterOpt = Option.createBuilder<Boolean>()
                .name(Component.literal("Live Anti-Stutter Engine"))
                .description(
                    OptionDescription.of(
                        Component.literal("Smooths out chunk loading dynamically while moving/flying to prevent FPS drops.")
                    )
                )
                .binding(
                    Smoothchunks.enabled,
                    { Smoothchunks.enabled },
                    { newVal ->
                        Smoothchunks.enabled = newVal
                        ClientPlayNetworking.send(PreloadPayload(PreloadPayload.ACTION_TOGGLE, 0))
                    }
                )
                .controller(BooleanControllerBuilder::create)
                .build()

            val radiusOpt = Option.createBuilder<Int>()
                .name(Component.literal("Pre-generation Radius (Chunks)"))
                .description(
                    OptionDescription.of(
                        Component.literal("Select radius for chunk generation.\n30 chunks = ~480 blocks\n60 chunks = ~1,000 blocks\n125 chunks = ~2,000 blocks\n250 chunks = ~4,000 blocks (Max)")
                    )
                )
                .binding(
                    pregenRadius,
                    { pregenRadius },
                    { newVal -> pregenRadius = newVal.coerceIn(1, 250) }
                )
                .controller { opt ->
                    IntegerSliderControllerBuilder.create(opt)
                        .range(5, 250)
                        .step(5)
                }
                .build()

            val startPregenOpt = Option.createBuilder<Boolean>()
                .name(Component.literal("Start Pre-generation Now"))
                .description(
                    OptionDescription.of(
                        Component.literal("Turn ON to launch background mass pre-generation for the selected radius! Progress and countdown timer will display on your Action Bar.")
                    )
                )
                .binding(
                    false,
                    { false },
                    { shouldStart ->
                        if (shouldStart) {
                            ClientPlayNetworking.send(PreloadPayload(PreloadPayload.ACTION_START, pregenRadius))
                        }
                    }
                )
                .controller(BooleanControllerBuilder::create)
                .build()

            val generalGroup = OptionGroup.createBuilder()
                .name(Component.literal("General Optimization"))
                .option(antiStutterOpt)
                .build()

            val pregenGroup = OptionGroup.createBuilder()
                .name(Component.literal("Mass Chunk Pre-generator"))
                .option(radiusOpt)
                .option(startPregenOpt)
                .build()

            return YetAnotherConfigLib.createBuilder()
                .title(Component.literal("SmoothChunks Configuration"))
                .category(
                    ConfigCategory.createBuilder()
                        .name(Component.literal("Settings"))
                        .group(generalGroup)
                        .group(pregenGroup)
                        .build()
                )
                .build()
                .generateScreen(parent)
        }
    }
}
