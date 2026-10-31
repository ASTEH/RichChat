package dev.asteh.richchat.network;

import dev.asteh.richchat.RichChat;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Временный диагностический C2S payload для проверки регистрации протокола. */
public record C2SPingPayload(long nonce) implements CustomPacketPayload {
    public static final Type<C2SPingPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RichChat.MOD_ID, "ping"));

    public static final StreamCodec<ByteBuf, C2SPingPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.LONG, C2SPingPayload::nonce,
            C2SPingPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
