package my.smoothchunks

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

class PreloadPayload(val action: Int, val radius: Int) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        const val ACTION_START = 0
        const val ACTION_STOP = 1
        const val ACTION_TOGGLE = 2

        val TYPE: CustomPacketPayload.Type<PreloadPayload> =
            CustomPacketPayload.Type(Identifier.fromNamespaceAndPath(Smoothchunks.MOD_ID, "preload"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, PreloadPayload> =
            StreamCodec.composite(
                ByteBufCodecs.INT, { p: PreloadPayload -> p.action },
                ByteBufCodecs.INT, { p: PreloadPayload -> p.radius },
                { act, rad -> PreloadPayload(act, rad) }
            )
    }
}
