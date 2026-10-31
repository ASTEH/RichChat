package dev.asteh.richchat.network;

import dev.asteh.richchat.RichChat;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Описывает загрузку до передачи байтовыми чанками. */
public record C2SUploadImageStartPayload(
        String uploadId,
        String fileName,
        int requestedCategory,
        int totalBytes,
        int declaredWidth,
        int declaredHeight,
        int chunkCount
) implements CustomPacketPayload {
    public static final Type<C2SUploadImageStartPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RichChat.MOD_ID, "upload_start"));

    private static final StreamCodec<ByteBuf, String> ID_CODEC = ByteBufCodecs.stringUtf8(36);
    private static final StreamCodec<ByteBuf, String> NAME_CODEC = ByteBufCodecs.stringUtf8(128);

    public static final StreamCodec<ByteBuf, C2SUploadImageStartPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                ID_CODEC.encode(buffer, value.uploadId);
                NAME_CODEC.encode(buffer, value.fileName);
                ByteBufCodecs.VAR_INT.encode(buffer, value.requestedCategory);
                ByteBufCodecs.VAR_INT.encode(buffer, value.totalBytes);
                ByteBufCodecs.VAR_INT.encode(buffer, value.declaredWidth);
                ByteBufCodecs.VAR_INT.encode(buffer, value.declaredHeight);
                ByteBufCodecs.VAR_INT.encode(buffer, value.chunkCount);
            },
            buffer -> new C2SUploadImageStartPayload(
                    ID_CODEC.decode(buffer),
                    NAME_CODEC.decode(buffer),
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
