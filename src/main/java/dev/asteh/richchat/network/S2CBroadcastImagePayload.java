package dev.asteh.richchat.network;

import dev.asteh.richchat.RichChat;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Метаданные принятого сервером изображения. */
public record S2CBroadcastImagePayload(
        String imageId,
        String senderName,
        String displayName,
        String extension,
        int category,
        int width,
        int height,
        int totalBytes,
        int chunkCount
) implements CustomPacketPayload {
    public static final Type<S2CBroadcastImagePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RichChat.MOD_ID, "image_metadata"));

    private static final StreamCodec<ByteBuf, String> ID_CODEC = ByteBufCodecs.stringUtf8(36);
    private static final StreamCodec<ByteBuf, String> SENDER_CODEC = ByteBufCodecs.stringUtf8(64);
    private static final StreamCodec<ByteBuf, String> NAME_CODEC = ByteBufCodecs.stringUtf8(128);
    private static final StreamCodec<ByteBuf, String> EXTENSION_CODEC = ByteBufCodecs.stringUtf8(8);

    public static final StreamCodec<ByteBuf, S2CBroadcastImagePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                ID_CODEC.encode(buffer, value.imageId);
                SENDER_CODEC.encode(buffer, value.senderName);
                NAME_CODEC.encode(buffer, value.displayName);
                EXTENSION_CODEC.encode(buffer, value.extension);
                ByteBufCodecs.VAR_INT.encode(buffer, value.category);
                ByteBufCodecs.VAR_INT.encode(buffer, value.width);
                ByteBufCodecs.VAR_INT.encode(buffer, value.height);
                ByteBufCodecs.VAR_INT.encode(buffer, value.totalBytes);
                ByteBufCodecs.VAR_INT.encode(buffer, value.chunkCount);
            },
            buffer -> new S2CBroadcastImagePayload(
                    ID_CODEC.decode(buffer),
                    SENDER_CODEC.decode(buffer),
                    NAME_CODEC.decode(buffer),
                    EXTENSION_CODEC.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer)
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
