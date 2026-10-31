package dev.asteh.richchat.network;

import dev.asteh.richchat.RichChat;
import dev.asteh.richchat.server.StoredImage;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Метаданные изображений, которые сервер отправляет клиенту при подключении. */
public record S2CChatHistoryImagesPayload(List<StoredImage> images) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 1000;

    public static final Type<S2CChatHistoryImagesPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RichChat.MOD_ID, "chat_image_history"));

    private static final StreamCodec<ByteBuf, String> ID_CODEC = ByteBufCodecs.stringUtf8(36);
    private static final StreamCodec<ByteBuf, String> SENDER_CODEC = ByteBufCodecs.stringUtf8(64);
    private static final StreamCodec<ByteBuf, String> NAME_CODEC = ByteBufCodecs.stringUtf8(128);
    private static final StreamCodec<ByteBuf, String> EXTENSION_CODEC = ByteBufCodecs.stringUtf8(8);

    public static final StreamCodec<ByteBuf, S2CChatHistoryImagesPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                if (value.images.size() > MAX_ENTRIES) {
                    throw new IllegalArgumentException("Too many RichChat history entries.");
                }
                ByteBufCodecs.VAR_INT.encode(buffer, value.images.size());
                for (StoredImage image : value.images) {
                    encodeImage(buffer, image);
                }
            },
            buffer -> {
                int count = ByteBufCodecs.VAR_INT.decode(buffer);
                if (count < 0 || count > MAX_ENTRIES) {
                    throw new DecoderException("Invalid RichChat history entry count: " + count);
                }
                List<StoredImage> images = new ArrayList<>(count);
                for (int index = 0; index < count; index++) {
                    images.add(decodeImage(buffer));
                }
                return new S2CChatHistoryImagesPayload(images);
            }
    );

    public S2CChatHistoryImagesPayload {
        images = List.copyOf(images);
        if (images.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Too many RichChat history entries.");
        }
    }

    private static void encodeImage(ByteBuf buffer, StoredImage image) {
        ID_CODEC.encode(buffer, image.id());
        SENDER_CODEC.encode(buffer, image.senderName());
        NAME_CODEC.encode(buffer, image.displayName());
        EXTENSION_CODEC.encode(buffer, image.extension());
        ByteBufCodecs.VAR_INT.encode(buffer, image.category());
        ByteBufCodecs.VAR_INT.encode(buffer, image.width());
        ByteBufCodecs.VAR_INT.encode(buffer, image.height());
        ByteBufCodecs.VAR_INT.encode(buffer, image.sizeBytes());
        ByteBufCodecs.VAR_LONG.encode(buffer, image.createdAtMillis());
    }

    private static StoredImage decodeImage(ByteBuf buffer) {
        return new StoredImage(
                ID_CODEC.decode(buffer),
                SENDER_CODEC.decode(buffer),
                NAME_CODEC.decode(buffer),
                EXTENSION_CODEC.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_LONG.decode(buffer)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
