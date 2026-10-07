package my.smoothchunks

import java.util.ArrayDeque
import kotlin.math.cos
import kotlin.math.sin
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.ChatFormatting
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.status.ChunkStatus

object Smoothchunks : ModInitializer {
    const val MOD_ID = "smoothchunks"

    var enabled = true
    private var preloadedCount = 0L

    private var isMassPreloading = false
    private var massTotalChunks = 0
    private var massCompletedChunks = 0
    private var massStartTime = 0L
    private var massPlayer: ServerPlayer? = null
    private val massQueue = ArrayDeque<ChunkPos>()

    private val liveQueue = ArrayDeque<Pair<ServerLevel, ChunkPos>>()
    private val liveRequested = HashSet<ChunkPos>()
    private var tickTimer = 0

    override fun onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(PreloadPayload.TYPE, PreloadPayload.CODEC)
        ServerPlayNetworking.registerGlobalReceiver(PreloadPayload.TYPE) { payload, context ->
            handlePayload(context.player(), payload)
        }

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                Commands.literal("smoothchunks")
                    .executes { ctx ->
                        val player = ctx.source.player
                        enabled = !enabled
                        val status = if (enabled) Component.literal("ENABLED").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                        else Component.literal("DISABLED").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)

                        val msg = Component.literal("[SmoothChunks] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                            .append(status)
                            .append(Component.literal("  \u2022  Preloaded: $preloadedCount chunks").withStyle(ChatFormatting.GRAY))
                        player?.sendSystemMessage(msg)
                        1
                    }
            )
        }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            tickTimer++

            if (isMassPreloading) {
                processMassPreloading()
            }

            if (enabled) {
                for (player in server.playerList.players) {
                    if (player.isSpectator) continue
                    predictAndQueueAhead(player)
                }
                processLiveQueue()
            }
        }
    }

    private fun handlePayload(player: ServerPlayer, p: PreloadPayload) {
        when (p.action) {
            PreloadPayload.ACTION_START -> startMassPreload(player, p.radius.coerceIn(1, 250))
            PreloadPayload.ACTION_STOP -> stopMassPreload(player, announce = true)
            PreloadPayload.ACTION_TOGGLE -> enabled = !enabled
        }
    }

    private fun startMassPreload(player: ServerPlayer, radiusChunks: Int) {
        massPlayer = player
        massQueue.clear()
        massCompletedChunks = 0
        massStartTime = System.currentTimeMillis()

        val centerChunkX = player.blockX shr 4
        val centerChunkZ = player.blockZ shr 4
        val level = player.level() as? ServerLevel ?: return
        val chunkSource = level.chunkSource

        val rSq = radiusChunks * radiusChunks
        for (dx in -radiusChunks..radiusChunks) {
            for (dz in -radiusChunks..radiusChunks) {
                if (dx * dx + dz * dz <= rSq) {
                    val targetX = centerChunkX + dx
                    val targetZ = centerChunkZ + dz
                    if (!chunkSource.hasChunk(targetX, targetZ)) {
                        massQueue.add(ChunkPos(targetX, targetZ))
                    }
                }
            }
        }

        massTotalChunks = massQueue.size
        if (massTotalChunks == 0) {
            player.sendSystemMessage(
                Component.literal("✔ All chunks in range are already generated!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                true
            )
            return
        }

        isMassPreloading = true
        player.sendSystemMessage(
            Component.literal("⚡ Starting pre-generation of $massTotalChunks chunks...").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
            true
        )
    }

    private fun stopMassPreload(player: ServerPlayer?, announce: Boolean) {
        isMassPreloading = false
        massQueue.clear()
        if (announce && player != null) {
            player.sendSystemMessage(
                Component.literal("⏹ Pre-generation stopped.").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                true
            )
        }
    }

    private fun formatTime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return when {
            h > 0 -> "${h}h ${m}m ${s}s"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }

    private fun processMassPreloading() {
        val player = massPlayer
        if (player == null || !player.isAlive) {
            stopMassPreload(null, false)
            return
        }

        val level = player.level() as? ServerLevel ?: return
        val chunkSource = level.chunkSource

        for (i in 0 until 3) {
            if (massQueue.isEmpty()) break
            val pos = massQueue.poll() ?: break
            try {
                chunkSource.getChunkFuture(pos.x, pos.z, ChunkStatus.FULL, true)
                massCompletedChunks++
                preloadedCount++
            } catch (_: Exception) {}
        }

        if (tickTimer % 10 == 0) {
            val elapsedSec = maxOf(0.1, (System.currentTimeMillis() - massStartTime) / 1000.0)
            val speed = massCompletedChunks / elapsedSec
            val remainingChunks = maxOf(0, massTotalChunks - massCompletedChunks)
            val remainingSec = if (speed > 0) (remainingChunks / speed).toLong() else 0L

            val percent = if (massTotalChunks > 0) (massCompletedChunks * 100) / massTotalChunks else 100
            val barsFilled = (percent / 10).coerceIn(0, 10)
            val barStr = "█".repeat(barsFilled) + "░".repeat(10 - barsFilled)

            val hudMsg = Component.literal("⚡ [")
                .append(Component.literal(barStr).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("] $percent% ($massCompletedChunks/$massTotalChunks)  |  ⏱️ Remaining: "))
                .append(Component.literal(formatTime(remainingSec)).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))

            player.sendSystemMessage(hudMsg, true)
        }

        if (massQueue.isEmpty()) {
            isMassPreloading = false
            level.playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f)
            val doneMsg = Component.literal("✔ Completed! $massTotalChunks chunks preloaded!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
            player.sendSystemMessage(doneMsg, true)
        }
    }

    private fun predictAndQueueAhead(player: ServerPlayer) {
        val level = player.level() as? ServerLevel ?: return
        val chunkSource = level.chunkSource

        val velX = player.x - player.xo
        val velZ = player.z - player.zo
        val speedSq = velX * velX + velZ * velZ
        if (speedSq < 0.0004 && !player.isFallFlying) return

        val currentChunkX = player.blockX shr 4
        val currentChunkZ = player.blockZ shr 4

        if (liveRequested.size > 200) {
            liveRequested.removeIf { pos ->
                val dx = pos.x - currentChunkX
                val dz = pos.z - currentChunkZ
                (dx * dx + dz * dz) > 144
            }
        }

        val yawRad = Math.toRadians(player.yRot.toDouble())
        val dirX = -sin(yawRad)
        val dirZ = cos(yawRad)
        val maxDist = if (player.isFallFlying || speedSq > 0.15) 5 else 3

        for (dist in 2..maxDist) {
            val centerX = currentChunkX + Math.round(dirX * dist).toInt()
            val centerZ = currentChunkZ + Math.round(dirZ * dist).toInt()

            val candidates = arrayOf(
                ChunkPos(centerX, centerZ),
                ChunkPos(centerX + Math.round(-dirZ).toInt(), centerZ + Math.round(dirX).toInt()),
                ChunkPos(centerX + Math.round(dirZ).toInt(), centerZ + Math.round(-dirX).toInt())
            )

            for (pos in candidates) {
                if (liveRequested.contains(pos) || chunkSource.hasChunk(pos.x, pos.z)) continue
                liveRequested.add(pos)
                liveQueue.add(Pair(level, pos))
            }
        }
    }

    private fun processLiveQueue() {
        if (liveQueue.isEmpty()) return
        val (level, pos) = liveQueue.poll() ?: return
        val chunkSource = level.chunkSource
        if (chunkSource.hasChunk(pos.x, pos.z)) return

        try {
            chunkSource.getChunkFuture(pos.x, pos.z, ChunkStatus.FULL, true)
            preloadedCount++
        } catch (_: Exception) {}
    }
}
