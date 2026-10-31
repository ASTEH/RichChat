package dev.asteh.richchat.network;

import dev.asteh.richchat.RichChat;
import dev.asteh.richchat.server.ImageUploadService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Регистрация версионированного протокола RichChat. */
public final class RichChatNetworking {
    private RichChatNetworking() {}

    public static void registerPayloads(final RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");

        registrar.playToServer(C2SPingPayload.TYPE, C2SPingPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        PacketDistributor.sendToPlayer(player, new S2CPongPayload(payload.nonce()));
                    }
                }));

        registrar.playToServer(C2SUploadImageStartPayload.TYPE, C2SUploadImageStartPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        ImageUploadService.begin(player, payload);
                    }
                }));

        registrar.playToServer(C2SUploadImageChunkPayload.TYPE, C2SUploadImageChunkPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        ImageUploadService.acceptChunk(player, payload);
                    }
                }));

        registrar.playToClient(S2CPongPayload.TYPE, S2CPongPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        RichChat.LOGGER.debug("Received RichChat pong: {}", payload.nonce())));

        registrar.playToClient(S2CBroadcastImagePayload.TYPE, S2CBroadcastImagePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        RichChat.LOGGER.debug("RichChat image metadata received: {}", payload.imageId())));

        registrar.playToClient(S2CChunkImagePayload.TYPE, S2CChunkImagePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    // Клиентский сборщик чанков будет подключён на следующем этапе.
                    RichChat.LOGGER.debug("RichChat image chunk received: {} / {}",
                            payload.imageId(), payload.chunkIndex());
                }));
    }
}
