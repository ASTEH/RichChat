package dev.asteh.richchat.network;

import dev.asteh.richchat.RichChat;
import dev.asteh.richchat.server.ImageUploadService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Чанк уже принятого изображения для клиентов. */
public record S2CChunkImagePayload(String imageId, int chunkIndex, byte[] data)
        implements CustomPacketPayload {
    public static final Type<S2CChunkImagePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RichChat.MOD_ID, "image_chunk"));

    private static final StreamCodec<ByteBuf, String> ID_CODEC = ByteBufCodecs.stringUtf8(36);
    private static final StreamCodec<ByteBuf, byte[]> DATA_CODEC =
            ByteBufCodecs.byteArray(ImageUploadService.DOWNLOAD_CHUNK_BYTES);

    public static final StreamCodec<ByteBuf, S2CChunkImagePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                ID_CODEC.encode(buffer, value.imageId);
                ByteBufCodecs.VAR_INT.encode(buffer, value.chunkIndex);
                DATA_CODEC.encode(buffer, value.data);
            },
            buffer -> new S2CChunkImagePayload(
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
