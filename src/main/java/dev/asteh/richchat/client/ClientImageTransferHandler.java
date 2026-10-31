package dev.asteh.richchat.client;

import dev.asteh.richchat.RichChat;
import dev.asteh.richchat.client.storage.LocalImageLibrary;
import dev.asteh.richchat.network.ImageTransferConstants;
import dev.asteh.richchat.network.S2CBroadcastImagePayload;
import dev.asteh.richchat.network.S2CChunkImagePayload;
import dev.asteh.richchat.server.ImageValidator;
import dev.asteh.richchat.server.StoredImage;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Сборщик входящих серверных изображений. Вызывается на главном потоке клиента. */
public final class ClientImageTransferHandler {
    private static final int MAX_INCOMING_IMAGE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_ACTIVE_TRANSFERS = 16;
    private static final Map<String, IncomingTransfer> TRANSFERS = new LinkedHashMap<>();

    private ClientImageTransferHandler() {}

    public static void handleMetadata(S2CBroadcastImagePayload payload) {
        if (!isValidUuid(payload.imageId())
                || payload.category() < 0 || payload.category() > 2
                || payload.totalBytes() <= 0 || payload.totalBytes() > MAX_INCOMING_IMAGE_BYTES
                || payload.width() <= 0 || payload.height() <= 0
                || payload.width() > 8192 || payload.height() > 8192
                || !isAllowedExtension(payload.extension())) {
            RichChat.LOGGER.warn("Discarded invalid RichChat image metadata: {}", payload.imageId());
            return;
        }

        int expectedChunks = (payload.totalBytes() + ImageTransferConstants.DOWNLOAD_CHUNK_BYTES - 1)
                / ImageTransferConstants.DOWNLOAD_CHUNK_BYTES;
        if (payload.chunkCount() != expectedChunks || expectedChunks <= 0) {
            RichChat.LOGGER.warn("Discarded RichChat metadata with an invalid chunk count: {}", payload.imageId());
            return;
        }

        TRANSFERS.remove(payload.imageId());
        while (TRANSFERS.size() >= MAX_ACTIVE_TRANSFERS) {
            Iterator<String> iterator = TRANSFERS.keySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            } else {
                break;
            }
        }

        StoredImage stored = new StoredImage(
                payload.imageId(),
                payload.senderName(),
                payload.displayName(),
                payload.extension(),
                payload.category(),
                payload.width(),
                payload.height(),
                payload.totalBytes(),
                System.currentTimeMillis()
        );
        TRANSFERS.put(payload.imageId(), new IncomingTransfer(stored, payload.chunkCount()));
    }

    public static void handleChunk(S2CChunkImagePayload payload) {
        IncomingTransfer transfer = TRANSFERS.get(payload.imageId());
        if (transfer == null) {
            return;
        }

        int index = payload.chunkIndex();
        if (index < 0 || index >= transfer.chunks.length || transfer.chunks[index] != null) {
            TRANSFERS.remove(payload.imageId());
            RichChat.LOGGER.warn("Discarded duplicate or out-of-range RichChat image chunk: {}", payload.imageId());
            return;
        }

        int expectedLength = Math.min(
                ImageTransferConstants.DOWNLOAD_CHUNK_BYTES,
                transfer.image.sizeBytes() - index * ImageTransferConstants.DOWNLOAD_CHUNK_BYTES
        );
        if (payload.data().length != expectedLength) {
            TRANSFERS.remove(payload.imageId());
            RichChat.LOGGER.warn("Discarded RichChat image chunk with an invalid length: {}", payload.imageId());
            return;
        }

        transfer.chunks[index] = payload.data().clone();
        transfer.received++;

        if (transfer.received == transfer.chunks.length) {
            TRANSFERS.remove(payload.imageId());
            finish(transfer);
        }
    }

    private static void finish(IncomingTransfer transfer) {
        byte[] bytes = new byte[transfer.image.sizeBytes()];
        int offset = 0;
        for (byte[] chunk : transfer.chunks) {
            if (chunk == null || offset + chunk.length > bytes.length) {
                return;
            }
            System.arraycopy(chunk, 0, bytes, offset, chunk.length);
            offset += chunk.length;
        }
        if (offset != bytes.length) {
            return;
        }

        ImageValidator.Validation validation = ImageValidator.validate(bytes, 8192, 8192);
        if (!validation.accepted()
                || validation.width() != transfer.image.width()
                || validation.height() != transfer.image.height()
                || !matchesExtension(validation.format(), transfer.image.extension())) {
            RichChat.LOGGER.warn("Received RichChat image failed client-side validation: {}", transfer.image.id());
            return;
        }

        try {
            LocalImageLibrary.saveReceived(transfer.image, bytes);
            RichChat.LOGGER.info("Received RichChat image {} from {}",
                    transfer.image.id(), transfer.image.senderName());
        } catch (IOException exception) {
            RichChat.LOGGER.error("Could not cache received RichChat image {}", transfer.image.id(), exception);
        }
    }

    private static boolean isValidUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isAllowedExtension(String extension) {
        return "png".equals(extension) || "jpg".equals(extension) || "gif".equals(extension);
    }

    private static boolean matchesExtension(String format, String extension) {
        return switch (format) {
            case "png" -> "png".equals(extension);
            case "jpeg" -> "jpg".equals(extension);
            case "gif" -> "gif".equals(extension);
            default -> false;
        };
    }

    private static final class IncomingTransfer {
        private final StoredImage image;
        private final byte[][] chunks;
        private int received;

        private IncomingTransfer(StoredImage image, int chunkCount) {
            this.image = image;
            this.chunks = new byte[chunkCount][];
        }
    }
}
