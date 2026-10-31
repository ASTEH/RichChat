package dev.asteh.richchat.network;

import dev.asteh.richchat.RichChat;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Один ограниченный фрагмент загружаемого изображения. */
public record C2SUploadImageChunkPayload(String uploadId, int chunkIndex, byte[] data)
        implements CustomPacketPayload {
    public static final Type<C2SUploadImageChunkPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RichChat.MOD_ID, "upload_chunk"));

    private static final StreamCodec<ByteBuf, String> ID_CODEC = ByteBufCodecs.stringUtf8(36);
    private static final StreamCodec<ByteBuf, byte[]> DATA_CODEC =
            ByteBufCodecs.byteArray(ImageTransferConstants.UPLOAD_CHUNK_BYTES);

    public static final StreamCodec<ByteBuf, C2SUploadImageChunkPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                ID_CODEC.encode(buffer, value.uploadId);
                ByteBufCodecs.VAR_INT.encode(buffer, value.chunkIndex);
                DATA_CODEC.encode(buffer, value.data);
            },
            buffer -> new C2SUploadImageChunkPayload(
                    ID_CODEC.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    DATA_CODEC.decode(buffer)
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
