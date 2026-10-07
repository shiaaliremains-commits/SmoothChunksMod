package my.smoothchunks.client

import com.mojang.blaze3d.platform.InputConstants
import my.smoothchunks.Smoothchunks
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.ChatFormatting
import net.minecraft.client.KeyMapping
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

object SmoothchunksClient : ClientModInitializer {
    private lateinit var toggleKey: KeyMapping

    override fun onInitializeClient() {
        val category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Smoothchunks.MOD_ID, "main"))

        // زر تفعيل / إطفاء السلاسة وعرض الإحصائيات (حرف K)
        toggleKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping("key.smoothchunks.toggle", InputConstants.KEY_K, category)
        )

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val player = client.player ?: return@register

            while (toggleKey.consumeClick()) {
                Smoothchunks.enabled = !Smoothchunks.enabled
                val status = if (Smoothchunks.enabled) {
                    Component.literal("ON (Anti-Stutter Active)").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                } else {
                    Component.literal("OFF").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                }

                player.sendSystemMessage(
                    Component.literal("[SmoothChunks] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        .append(Component.literal("Smooth Loader: ").withStyle(ChatFormatting.WHITE))
                        .append(status)
                )
            }
        }
    }
}
