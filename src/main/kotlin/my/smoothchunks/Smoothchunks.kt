package my.smoothchunks

import java.util.ArrayDeque
import kotlin.math.cos
import kotlin.math.sin
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.ChatFormatting
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.status.ChunkStatus

object Smoothchunks : ModInitializer {
    const val MOD_ID = "smoothchunks"

    var enabled = true
    private var preloadedCount = 0L

    private val queue = ArrayDeque<Pair<ServerLevel, ChunkPos>>()
    private val requestedChunks = HashSet<Long>()
    private var cleanTimer = 0

    override fun onInitialize() {
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
            if (!enabled) return@register

            cleanTimer++
            if (cleanTimer > 120) {
                cleanTimer = 0
                if (requestedChunks.size > 500) requestedChunks.clear()
            }

            for (player in server.playerList.players) {
                if (player.isSpectator) continue
                predictAndQueueAhead(player)
            }

            processQueueGradually()
        }
    }

    private fun predictAndQueueAhead(player: ServerPlayer) {
        val level = player.level() as? ServerLevel ?: return
        val chunkSource = level.chunkSource

        val currentChunkX = player.blockX shr 4
        val currentChunkZ = player.blockZ shr 4

        val yawRad = Math.toRadians(player.yRot.toDouble())
        val dirX = -sin(yawRad)
        val dirZ = cos(yawRad)

        for (dist in 2..4) {
            val aheadX = currentChunkX + Math.round(dirX * dist).toInt()
            val aheadZ = currentChunkZ + Math.round(dirZ * dist).toInt()

            for (ox in -1..1) {
                for (oz in -1..1) {
                    val targetX = aheadX + ox
                    val targetZ = aheadZ + oz
                    val chunkPos = ChunkPos(targetX, targetZ)
                    val posLong = chunkPos.toLong()

                    if (requestedChunks.contains(posLong) || chunkSource.hasChunk(targetX, targetZ)) {
                        continue
                    }

                    requestedChunks.add(posLong)
                    queue.add(Pair(level, chunkPos))
                }
            }
        }
    }

    private fun processQueueGradually() {
        if (queue.isEmpty()) return

        val (level, pos) = queue.poll() ?: return
        val chunkSource = level.chunkSource

        if (chunkSource.hasChunk(pos.x, pos.z)) return

        try {
            chunkSource.getChunkFuture(pos.x, pos.z, ChunkStatus.FULL, true)
            preloadedCount++
        } catch (_: Exception) {
            // ignore
        }
    }
}
