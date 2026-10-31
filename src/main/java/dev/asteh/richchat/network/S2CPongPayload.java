package dev.asteh.richchat.network;

import dev.asteh.richchat.RichChat;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Временный диагностический S2C payload для проверки обратного направления. */
public record S2CPongPayload(long nonce) implements CustomPacketPayload {
    public static final Type<S2CPongPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RichChat.MOD_ID, "pong"));

    public static final StreamCodec<ByteBuf, S2CPongPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.LONG, S2CPongPayload::nonce,
            S2CPongPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
