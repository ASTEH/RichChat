package dev.asteh.richchat.client;

import dev.asteh.richchat.network.S2CBroadcastImagePayload;
import dev.asteh.richchat.network.S2CChatHistoryImagesPayload;
import dev.asteh.richchat.network.S2CChunkImagePayload;

/** Точки входа для clientbound payload; вызываются через изолированный dispatch. */
public final class ClientImagePayloads {
    private ClientImagePayloads() {}

    public static void handleMetadata(S2CBroadcastImagePayload payload) {
        ClientImageTransferHandler.handleMetadata(payload);
    }

    public static void handleHistory(S2CChatHistoryImagesPayload payload) {
        ClientImageTransferHandler.handleHistory(payload);
    }

    public static void handleChunk(S2CChunkImagePayload payload) {
        ClientImageTransferHandler.handleChunk(payload);
    }
}
