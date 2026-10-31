package dev.asteh.richchat.network;

import dev.asteh.richchat.RichChat;
import dev.asteh.richchat.server.ImageUploadService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Регистрация версионированного протокола RichChat. */
public final class RichChatNetworking {
    private static final String CLIENT_HANDLER = "dev.asteh.richchat.client.ClientImagePayloads";

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
                        invokeClient("handleMetadata", S2CBroadcastImagePayload.class, payload)));

        registrar.playToClient(S2CChatHistoryImagesPayload.TYPE, S2CChatHistoryImagesPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        invokeClient("handleHistory", S2CChatHistoryImagesPayload.class, payload)));

        registrar.playToClient(S2CChunkImagePayload.TYPE, S2CChunkImagePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        invokeClient("handleChunk", S2CChunkImagePayload.class, payload)));
    }

    /* Client-only классы не должны загружаться в common-графе dedicated server. */
    private static void invokeClient(String methodName, Class<?> payloadType, Object payload) {
        try {
            Class<?> handler = Class.forName(CLIENT_HANDLER);
            Method method = handler.getMethod(methodName, payloadType);
            method.invoke(null, payload);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            RichChat.LOGGER.error("Could not dispatch client payload {}", methodName, exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("RichChat client payload failed: " + methodName, cause);
        }
    }
}
