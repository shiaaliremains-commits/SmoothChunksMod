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

    // قائمة انتظار الشنكات لتوزيع حمل المعالجة
    private val queue = ArrayDeque<Pair<ServerLevel, ChunkPos>>()
    private val requestedChunks = HashSet<Long>()
    private var cleanTimer = 0

    override fun onInitialize() {
        // تسجيل أمر الشات /smoothchunks
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

        // محرك المعالجة والتقسيط في نهاية كل تيك للسيرفر
        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (!enabled) return@register

            cleanTimer++
            if (cleanTimer > 120) { // تنظيف الذاكرة المؤقتة كل 6 ثوانٍ
                cleanTimer = 0
                if (requestedChunks.size > 500) requestedChunks.clear()
            }

            // 1. التنبؤ بالشنكات المستقبلية بناءً على حركة اللاعبين
            for (player in server.playerList.players) {
                if (player.isSpectator) continue
                predictAndQueueAhead(player)
            }

            // 2. معالجة وتوليد شنك واحد فقط بالخلفية بدون الضغط على المعالج
            processQueueGradually()
        }
    }

    private fun predictAndQueueAhead(player: ServerPlayer) {
        val level = player.level() as? ServerLevel ?: return
        val chunkSource = level.chunkSource

        // إحداثيات الشنك الحالي للاعب
        val currentChunkX = player.blockX shr 4
        val currentChunkZ = player.blockZ shr 4

        // قراءة زاوية نظر اللاعب
        val yawRad = Math.toRadians(player.yRot.toDouble())
        val dirX = -sin(yawRad)
        val dirZ = cos(yawRad)

        // حساب الشنكات كدام اللاعب بمسافة 2 إلى 4 شنكات
        for (dist in 2..4) {
            val aheadX = currentChunkX + Math.round(dirX * dist).toInt()
            val aheadZ = currentChunkZ + Math.round(dirZ * dist).toInt()

            // فحص الشنك الرئيسي والشنكات المجاورة له في زاوية الرؤية
            for (ox in -1..1) {
                for (oz in -1..1) {
                    val targetX = aheadX + ox
                    val targetZ = aheadZ + oz
                    val posLong = ChunkPos.asLong(targetX, targetZ)

                    // إذا الشنك مولد مسبقاً أو مطلوب بالخلفية، نتجاهله
                    if (requestedChunks.contains(posLong) || chunkSource.hasChunk(targetX, targetZ)) {
                        continue
                    }

                    // إضافته لقائمة التحميل المقسّط
                    requestedChunks.add(posLong)
                    queue.add(Pair(level, ChunkPos(targetX, targetZ)))
                }
            }
        }
    }

    // تنقيط التوليد: توليد شنك واحد فقط بكل تيك
    private fun processQueueGradually() {
        if (queue.isEmpty()) return

        val (level, pos) = queue.poll() ?: return
        val chunkSource = level.chunkSource

        // إذا أصبح موجوداً بالذاكرة نتخطاه
        if (chunkSource.hasChunk(pos.x, pos.z)) return

        // طلب الشنك في خيوط المعالجة الخلفية لماينكرافت (Async Future)
        try {
            chunkSource.getChunkFuture(pos.x, pos.z, ChunkStatus.FULL, true)
            preloadedCount++
        } catch (_: Exception) {
            // حماية ضد أي أخطاء مفاجئة
        }
    }
}
