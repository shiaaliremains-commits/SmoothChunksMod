package my.smoothchunks.client

import my.smoothchunks.PreloadPayload
import my.smoothchunks.Smoothchunks
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class SmoothChunksScreen(private val parent: Screen? = null) : Screen(Component.literal("SmoothChunks Control")) {
    private lateinit var inputRadiusBox: EditBox
    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var toggleLiveBtn: Button
    private var radiusChunks = 30

    private fun getParsedRadius(): Int {
        val txt = inputRadiusBox.value.trim()
        val num = txt.toIntOrNull() ?: 30
        return num.coerceIn(1, 250) // حتى 250 شنك = 4000 بلوكة
    }

    override fun onClose() {
        if (parent != null) {
            minecraft?.setScreen(parent)
        } else {
            super.onClose()
        }
    }

    override fun init() {
        val w = 240
        val left = width / 2 - w / 2
        var y = height / 2 - 80

        val header = Button.builder(
            Component.literal("⚡ SmoothChunks Optimizer").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
        ) { _ -> }.bounds(left, y, w, 20).build()
        header.active = false
        addRenderableWidget(header)
        y += 26

        toggleLiveBtn = Button.builder(getLiveStatusComponent()) { _ ->
            Smoothchunks.enabled = !Smoothchunks.enabled
            toggleLiveBtn.message = getLiveStatusComponent()
            ClientPlayNetworking.send(PreloadPayload(PreloadPayload.ACTION_TOGGLE, 0))
        }.bounds(left, y, w, 20).build()
        addRenderableWidget(toggleLiveBtn)
        y += 30

        // المربع الأسود الداكن
        inputRadiusBox = EditBox(font, left + 4, y, w - 8, 20, Component.literal("Radius (1-250)"))
        inputRadiusBox.setMaxLength(4)
        inputRadiusBox.value = radiusChunks.toString()
        inputRadiusBox.setTextColor(0x00FF66)
        addRenderableWidget(inputRadiusBox)
        setInitialFocus(inputRadiusBox)
        y += 24

        val infoLabel = Button.builder(
            Component.literal("Max: 250 Chunks (= 4,000 Blocks)").withStyle(ChatFormatting.DARK_GRAY)
        ) { _ -> }.bounds(left, y, w, 14).build()
        infoLabel.active = false
        addRenderableWidget(infoLabel)
        y += 20

        startBtn = Button.builder(Component.literal("▶ Start Preload").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)) { _ ->
            radiusChunks = getParsedRadius()
            ClientPlayNetworking.send(PreloadPayload(PreloadPayload.ACTION_START, radiusChunks))
            onClose()
        }.bounds(left, y, w / 2 - 2, 20).build()
        addRenderableWidget(startBtn)

        stopBtn = Button.builder(Component.literal("⏹ Stop").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)) { _ ->
            ClientPlayNetworking.send(PreloadPayload(PreloadPayload.ACTION_STOP, 0))
            onClose()
        }.bounds(left + w / 2 + 2, y, w / 2 - 2, 20).build()
        addRenderableWidget(stopBtn)
        y += 26

        addRenderableWidget(
            Button.builder(Component.literal("Done")) { _ -> onClose() }
                .bounds(left, y, w, 20).build()
        )
    }

    private fun getLiveStatusComponent(): Component {
        val state = if (Smoothchunks.enabled) Component.literal("ON").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
        else Component.literal("OFF").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
        return Component.literal("Live Anti-Stutter: ").append(state)
    }
}
