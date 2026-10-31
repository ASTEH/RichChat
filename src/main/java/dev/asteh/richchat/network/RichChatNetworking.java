package dev.asteh.richchat.network;

import dev.asteh.richchat.RichChat;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Единая точка регистрации CustomPacketPayload.
 *
 * Диагностические ping/pong-пакеты будут удалены, когда будет добавлен реальный
 * протокол загрузки изображений. Для игровых сообщений используется версия "1".
 */
public final class RichChatNetworking {
    private RichChatNetworking() {}

    public static void registerPayloads(final RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");

        registrar.playToServer(C2SPingPayload.TYPE, C2SPingPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handlePing(payload, context)));

        registrar.playToClient(S2CPongPayload.TYPE, S2CPongPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        RichChat.LOGGER.debug("Received RichChat pong: {}", payload.nonce())));
    }

    private static void handlePing(final C2SPingPayload payload, final IPayloadContext context) {
        if (context.player() instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new S2CPongPayload(payload.nonce()));
        } else {
            RichChat.LOGGER.warn("Discarded RichChat ping outside a server-player context");
        }
    }
}
