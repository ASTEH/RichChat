package dev.asteh.richchat.client;

import dev.asteh.richchat.RichChat;
import dev.asteh.richchat.client.storage.LocalImageLibrary;
import dev.asteh.richchat.network.ImageTransferConstants;
import dev.asteh.richchat.network.S2CBroadcastImagePayload;
import dev.asteh.richchat.network.S2CChatHistoryImagesPayload;
import dev.asteh.richchat.network.S2CChunkImagePayload;
import dev.asteh.richchat.server.ImageValidator;
import dev.asteh.richchat.server.StoredImage;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Последовательно собирает входящие изображения, не выделяя память под всю историю сразу. */
public final class ClientImageTransferHandler {
    private static final int MAX_INCOMING_IMAGE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_PENDING_TRANSFERS = 1000;

    private static final ArrayDeque<StoredImage> PENDING = new ArrayDeque<>();
    private static final Set<String> QUEUED_IDS = new HashSet<>();
    private static IncomingTransfer active;

    private ClientImageTransferHandler() {}

    public static void handleMetadata(S2CBroadcastImagePayload payload) {
        enqueue(new StoredImage(
                payload.imageId(),
                payload.senderName(),
                payload.displayName(),
                payload.extension(),
                payload.category(),
                payload.width(),
                payload.height(),
                payload.totalBytes(),
                System.currentTimeMillis()
        ));
    }

    public static void handleHistory(S2CChatHistoryImagesPayload payload) {
        for (StoredImage image : payload.images()) {
            enqueue(image);
        }
    }

    private static void enqueue(StoredImage image) {
        if (!isValidImageMetadata(image) || QUEUED_IDS.contains(image.id())) {
            RichChat.LOGGER.warn("Discarded invalid or repeated RichChat image metadata: {}", image.id());
            return;
        }
        if (PENDING.size() >= MAX_PENDING_TRANSFERS) {
            RichChat.LOGGER.warn("RichChat pending image queue is full; skipped {}", image.id());
            return;
        }

        PENDING.addLast(image);
        QUEUED_IDS.add(image.id());
        startNextIfNeeded();
    }

    public static void handleChunk(S2CChunkImagePayload payload) {
        if (active == null || !active.image.id().equals(payload.imageId())) {
            // Пакеты, не относящиеся к текущей передаче, отбрасываются.
            return;
        }

        int index = payload.chunkIndex();
        if (index < 0 || index >= active.chunks.length || active.chunks[index] != null) {
            abortActive("duplicate or out-of-range chunk");
            return;
        }

        int expectedLength = Math.min(
                ImageTransferConstants.DOWNLOAD_CHUNK_BYTES,
                active.image.sizeBytes() - index * ImageTransferConstants.DOWNLOAD_CHUNK_BYTES
        );
        if (payload.data().length != expectedLength) {
            abortActive("chunk has an invalid length");
            return;
        }

        active.chunks[index] = payload.data().clone();
        active.received++;

        if (active.received == active.chunks.length) {
            IncomingTransfer completed = active;
            active = null;
            finish(completed);
            startNextIfNeeded();
        }
    }

    private static void startNextIfNeeded() {
        if (active != null) {
            return;
        }

        StoredImage next = PENDING.pollFirst();
        if (next == null) {
            return;
        }
        active = new IncomingTransfer(next);
    }

    private static void abortActive(String reason) {
        if (active != null) {
            RichChat.LOGGER.warn("Aborted RichChat image {}: {}", active.image.id(), reason);
            QUEUED_IDS.remove(active.image.id());
            active = null;
            startNextIfNeeded();
        }
    }

    private static void finish(IncomingTransfer transfer) {
        byte[] bytes = new byte[transfer.image.sizeBytes()];
        int offset = 0;
        for (byte[] chunk : transfer.chunks) {
            if (chunk == null || offset + chunk.length > bytes.length) {
                RichChat.LOGGER.warn("Incomplete RichChat image {}", transfer.image.id());
                QUEUED_IDS.remove(transfer.image.id());
                return;
            }
            System.arraycopy(chunk, 0, bytes, offset, chunk.length);
            offset += chunk.length;
        }
        if (offset != bytes.length) {
            QUEUED_IDS.remove(transfer.image.id());
            return;
        }

        ImageValidator.Validation validation = ImageValidator.validate(bytes, 8192, 8192);
        if (!validation.accepted()
                || validation.width() != transfer.image.width()
                || validation.height() != transfer.image.height()
                || !matchesExtension(validation.format(), transfer.image.extension())) {
            RichChat.LOGGER.warn("Received RichChat image failed client-side validation: {}", transfer.image.id());
            QUEUED_IDS.remove(transfer.image.id());
            return;
        }

        try {
            LocalImageLibrary.saveReceived(transfer.image, bytes);
            RichChat.LOGGER.info("Received RichChat image {} from {}",
                    transfer.image.id(), transfer.image.senderName());
        } catch (IOException exception) {
            RichChat.LOGGER.error("Could not cache received RichChat image {}", transfer.image.id(), exception);
        } finally {
            QUEUED_IDS.remove(transfer.image.id());
        }
    }

    private static boolean isValidImageMetadata(StoredImage image) {
        return isValidUuid(image.id())
                && image.category() >= 0 && image.category() <= 2
                && image.sizeBytes() > 0 && image.sizeBytes() <= MAX_INCOMING_IMAGE_BYTES
                && image.width() > 0 && image.height() > 0
                && image.width() <= 8192 && image.height() <= 8192
                && isAllowedExtension(image.extension());
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

        private IncomingTransfer(StoredImage image) {
            this.image = image;
            int count = (image.sizeBytes() + ImageTransferConstants.DOWNLOAD_CHUNK_BYTES - 1)
                    / ImageTransferConstants.DOWNLOAD_CHUNK_BYTES;
            this.chunks = new byte[count][];
        }
    }
}
