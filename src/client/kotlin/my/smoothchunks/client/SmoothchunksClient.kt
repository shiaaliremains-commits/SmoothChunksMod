package my.smoothchunks.client

import com.mojang.blaze3d.platform.InputConstants
import my.smoothchunks.Smoothchunks
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.resources.Identifier

object SmoothchunksClient : ClientModInitializer {
    private lateinit var menuKey: KeyMapping

    override fun onInitializeClient() {
        val category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Smoothchunks.MOD_ID, "main"))

        // زر حرف H لفتح شاشة التحكم والمربع الأسود
        menuKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping("key.smoothchunks.menu", InputConstants.KEY_H, category)
        )

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val player = client.player ?: return@register

            while (menuKey.consumeClick()) {
                openScreen(SmoothChunksScreen())
            }
        }
    }

    fun openScreen(screen: Screen?) {
        val mc = Minecraft.getInstance()
        val gui = runCatching { mc.javaClass.getField("gui").get(mc) }.getOrNull()
        if (!invokeSetScreen(gui, screen)) invokeSetScreen(mc, screen)
    }

    private fun invokeSetScreen(target: Any?, screen: Screen?): Boolean {
        if (target == null) return false
        val method = target.javaClass.methods.firstOrNull {
            (it.name == "setScreen" || it.name == "setScreenAndShow") && it.parameterCount == 1
        } ?: return false
        method.invoke(target, screen)
        return true
    }
}
