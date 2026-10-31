package dev.asteh.richchat.server;

import dev.asteh.richchat.RichChat;

import dev.asteh.richchat.config.RichChatServerConfig;
import dev.asteh.richchat.network.C2SUploadImageChunkPayload;
import dev.asteh.richchat.network.C2SUploadImageStartPayload;
import dev.asteh.richchat.network.ImageTransferConstants;
import dev.asteh.richchat.network.S2CBroadcastImagePayload;
import dev.asteh.richchat.network.S2CChatHistoryImagesPayload;
import dev.asteh.richchat.network.S2CChunkImagePayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Серверный менеджер загрузок. Все методы вызываются с главного серверного потока. */
public final class ImageUploadService {
    public static final int UPLOAD_CHUNK_BYTES = ImageTransferConstants.UPLOAD_CHUNK_BYTES;
    public static final int DOWNLOAD_CHUNK_BYTES = ImageTransferConstants.DOWNLOAD_CHUNK_BYTES;

    private static final int MAX_ACTIVE_UPLOADS = 16;
    private static final long UPLOAD_TIMEOUT_MS = 30_000L;
    private static final long COOLDOWN_TRACKING_TTL_MS = 120_000L;
    private static final int MAX_STICKER_DIMENSION = 128;

    private static final Map<UUID, UploadSession> ACTIVE_UPLOADS = new HashMap<>();
    private static final Map<UUID, Long> LAST_UPLOAD_AT = new HashMap<>();

    private ImageUploadService() {}

    public static void begin(ServerPlayer player, C2SUploadImageStartPayload payload) {
        cleanupExpired();

        if (!RichChatServerConfig.ENABLED.get()) {
            reject(player, "Image sharing is disabled by the server.");
            return;
        }

        UUID uploadId;
        try {
            uploadId = UUID.fromString(payload.uploadId());
        } catch (IllegalArgumentException exception) {
            reject(player, "Invalid upload identifier.");
            return;
        }

        int maxBytes = RichChatServerConfig.MAX_FILE_SIZE_KB.get() * 1024;
        int maxWidth = RichChatServerConfig.MAX_WIDTH.get();
        int maxHeight = RichChatServerConfig.MAX_HEIGHT.get();

        if (payload.totalBytes() <= 0 || payload.totalBytes() > maxBytes) {
            reject(player, "Image file exceeds the server size limit.");
            return;
        }
        if (payload.requestedCategory() < 0 || payload.requestedCategory() > 2) {
            reject(player, "Invalid image category.");
            return;
        }
        if (payload.declaredWidth() <= 0 || payload.declaredHeight() <= 0
                || payload.declaredWidth() > maxWidth || payload.declaredHeight() > maxHeight) {
            reject(player, "Declared image dimensions are outside the server limit.");
            return;
        }

        int expectedChunks = (payload.totalBytes() + UPLOAD_CHUNK_BYTES - 1) / UPLOAD_CHUNK_BYTES;
        if (payload.chunkCount() != expectedChunks || expectedChunks <= 0) {
            reject(player, "Invalid image chunk count.");
            return;
        }

        if (ACTIVE_UPLOADS.containsKey(player.getUUID())) {
            reject(player, "You already have an image upload in progress.");
            return;
        }
        if (ACTIVE_UPLOADS.size() >= MAX_ACTIVE_UPLOADS) {
            reject(player, "The server is currently processing too many image uploads.");
            return;
        }

        long now = System.currentTimeMillis();
        long cooldown = RichChatServerConfig.ANTI_SPAM_COOLDOWN_MS.get();
        long previous = LAST_UPLOAD_AT.getOrDefault(player.getUUID(), 0L);
        if (now - previous < cooldown) {
            reject(player, "Please wait before uploading another image.");
            return;
        }

        String displayName = sanitizeFileName(payload.fileName());
        if (displayName.isBlank()) {
            displayName = "image";
        }

        ACTIVE_UPLOADS.put(player.getUUID(), new UploadSession(
                uploadId,
                displayName,
                payload.requestedCategory(),
                payload.totalBytes(),
                payload.chunkCount(),
                now + UPLOAD_TIMEOUT_MS
        ));
        LAST_UPLOAD_AT.put(player.getUUID(), now);
        player.sendSystemMessage(Component.literal("RichChat: upload started."));
    }

    public static void acceptChunk(ServerPlayer player, C2SUploadImageChunkPayload payload) {
        UploadSession session = ACTIVE_UPLOADS.get(player.getUUID());
        if (session == null) {
            reject(player, "No active image upload was found.");
            return;
        }

        if (!session.id.toString().equals(payload.uploadId())) {
            reject(player, "Image upload identifier mismatch.");
            ACTIVE_UPLOADS.remove(player.getUUID());
            return;
        }

        if (System.currentTimeMillis() > session.expiresAtMillis) {
            ACTIVE_UPLOADS.remove(player.getUUID());
            reject(player, "Image upload timed out.");
            return;
        }

        int index = payload.chunkIndex();
        if (index < 0 || index >= session.chunks.length || session.chunks[index] != null) {
            ACTIVE_UPLOADS.remove(player.getUUID());
            reject(player, "Invalid or duplicate image chunk.");
            return;
        }

        int expectedLength = Math.min(UPLOAD_CHUNK_BYTES,
                session.totalBytes - index * UPLOAD_CHUNK_BYTES);
        if (payload.data().length != expectedLength) {
            ACTIVE_UPLOADS.remove(player.getUUID());
            reject(player, "Image chunk has an invalid size.");
            return;
        }

        session.chunks[index] = payload.data().clone();
        session.receivedChunks++;

        if (session.receivedChunks == session.chunks.length) {
            ACTIVE_UPLOADS.remove(player.getUUID());
            complete(player, session);
        }
    }

    private static void complete(ServerPlayer player, UploadSession session) {
        byte[] imageBytes = new byte[session.totalBytes];
        int offset = 0;
        for (byte[] chunk : session.chunks) {
            if (chunk == null || offset + chunk.length > imageBytes.length) {
                reject(player, "Image upload is incomplete.");
                return;
            }
            System.arraycopy(chunk, 0, imageBytes, offset, chunk.length);
            offset += chunk.length;
        }

        if (offset != imageBytes.length) {
            reject(player, "Image upload length does not match its declaration.");
            return;
        }

        ImageValidator.Validation validation = ImageValidator.validate(
                imageBytes,
                RichChatServerConfig.MAX_WIDTH.get(),
                RichChatServerConfig.MAX_HEIGHT.get()
        );
        if (!validation.accepted()) {
            reject(player, validation.reason());
            return;
        }

        int category;
        if (session.requestedCategory == 2) {
            if (validation.width() > MAX_STICKER_DIMENSION
                    || validation.height() > MAX_STICKER_DIMENSION) {
                reject(player, "This image is too large for a sticker. Upload it as a photo or GIF.");
                return;
            }
            category = 2;
        } else if (validation.animated()) {
            category = 1;
        } else {
            category = 0;
        }

        String extension = switch (validation.format()) {
            case "jpeg" -> "jpg";
            case "png" -> "png";
            case "gif" -> "gif";
            default -> throw new IllegalStateException("Validator returned an unsupported format.");
        };

        String imageId = session.id.toString();
        MinecraftServer server = player.getServer();
        if (server == null) {
            reject(player, "Server is no longer available.");
            return;
        }

        try {
            ImageStorage.store(server, imageId, extension, imageBytes);

            StoredImage stored = new StoredImage(
                    imageId,
                    player.getGameProfile().getName(),
                    session.displayName,
                    extension,
                    category,
                    validation.width(),
                    validation.height(),
                    imageBytes.length,
                    System.currentTimeMillis()
            );

            if (RichChatServerConfig.KEEP_HISTORY.get()) {
                ChatImageHistoryData.get(server).add(stored, RichChatServerConfig.HISTORY_SIZE.get());
            }

            sendImage(player.getServer(), stored, imageBytes);
            player.sendSystemMessage(Component.literal("RichChat: image accepted and sent to online players."));
        } catch (IOException exception) {
            player.sendSystemMessage(Component.literal("RichChat: could not store image (" + exception.getMessage() + ")."));
        }
    }

    private static void sendImage(MinecraftServer server, StoredImage stored, byte[] imageBytes) {
        int downloadChunks = (imageBytes.length + DOWNLOAD_CHUNK_BYTES - 1) / DOWNLOAD_CHUNK_BYTES;
        PacketDistributor.sendToAllPlayers(new S2CBroadcastImagePayload(
                stored.id(),
                stored.senderName(),
                stored.displayName(),
                stored.extension(),
                stored.category(),
                stored.width(),
                stored.height(),
                stored.sizeBytes(),
                downloadChunks
        ));

        for (int index = 0; index < downloadChunks; index++) {
            int start = index * DOWNLOAD_CHUNK_BYTES;
            int end = Math.min(imageBytes.length, start + DOWNLOAD_CHUNK_BYTES);
            byte[] part = java.util.Arrays.copyOfRange(imageBytes, start, end);
            PacketDistributor.sendToAllPlayers(new S2CChunkImagePayload(stored.id(), index, part));
        }
    }

    /** Отправляет подключившемуся игроку последние записи истории в пределах заданного бюджета. */
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !RichChatServerConfig.ENABLED.get()
                || !RichChatServerConfig.KEEP_HISTORY.get()
                || RichChatServerConfig.HISTORY_SIZE.get() <= 0) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        List<StoredImage> history = ChatImageHistoryData.get(server).entries();
        long maxTransferBytes = (long) RichChatServerConfig.MAX_HISTORY_TRANSFER_KB.get() * 1024L;
        if (history.isEmpty() || maxTransferBytes <= 0L) {
            PacketDistributor.sendToPlayer(player, new S2CChatHistoryImagesPayload(List.of()));
            return;
        }

        List<HistoryTransfer> transfers = new ArrayList<>();
        long totalBytes = 0L;

        // Идём от самых новых записей к старым: бюджет в первую очередь сохраняет свежую историю.
        for (int index = history.size() - 1; index >= 0; index--) {
            StoredImage image = history.get(index);
            if (transfers.size() >= S2CChatHistoryImagesPayload.MAX_ENTRIES
                    || image.sizeBytes() <= 0
                    || totalBytes + image.sizeBytes() > maxTransferBytes) {
                continue;
            }

            try {
                Path path = ImageStorage.resolve(server, image);
                long diskSize = Files.size(path);
                if (diskSize != image.sizeBytes() || diskSize > maxTransferBytes) {
                    RichChat.LOGGER.warn("Skipped RichChat history file with a size mismatch: {}", image.id());
                    continue;
                }

                byte[] bytes = Files.readAllBytes(path);
                transfers.add(new HistoryTransfer(image, bytes));
                totalBytes += bytes.length;
            } catch (IOException exception) {
                RichChat.LOGGER.warn("Could not read RichChat history image {}", image.id(), exception);
            }
        }

        if (transfers.isEmpty()) {
            PacketDistributor.sendToPlayer(player, new S2CChatHistoryImagesPayload(List.of()));
            return;
        }

        List<StoredImage> metadata = transfers.stream().map(HistoryTransfer::image).toList();
        PacketDistributor.sendToPlayer(player, new S2CChatHistoryImagesPayload(metadata));

        for (HistoryTransfer transfer : transfers) {
            sendImage(player, transfer.image(), transfer.bytes());
        }

        RichChat.LOGGER.debug("Sent {} RichChat history images ({} bytes) to {}",
                transfers.size(), totalBytes, player.getGameProfile().getName());
    }

    private static void sendImage(ServerPlayer player, StoredImage image, byte[] bytes) {
        int downloadChunks = (bytes.length + DOWNLOAD_CHUNK_BYTES - 1) / DOWNLOAD_CHUNK_BYTES;
        for (int index = 0; index < downloadChunks; index++) {
            int start = index * DOWNLOAD_CHUNK_BYTES;
            int end = Math.min(bytes.length, start + DOWNLOAD_CHUNK_BYTES);
            PacketDistributor.sendToPlayer(player, new S2CChunkImagePayload(
                    image.id(), index, java.util.Arrays.copyOfRange(bytes, start, end)));
        }
    }

    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE_UPLOADS.remove(event.getEntity().getUUID());
    }

    private static void cleanupExpired() {
        long now = System.currentTimeMillis();
        ACTIVE_UPLOADS.entrySet().removeIf(entry -> now > entry.getValue().expiresAtMillis);
        LAST_UPLOAD_AT.entrySet().removeIf(entry -> now - entry.getValue() > COOLDOWN_TRACKING_TTL_MS);
    }

    private static void reject(ServerPlayer player, String reason) {
        player.sendSystemMessage(Component.literal("RichChat: " + reason));
    }

    private static String sanitizeFileName(String supplied) {
        if (supplied == null) {
            return "";
        }
        String value = supplied.replace('\\', '_').replace('/', '_')
                .replaceAll("[^\\p{L}\\p{N}._ -]", "_")
                .trim();
        if (value.length() > 64) {
            value = value.substring(0, 64);
        }
        return value;
    }

    private record HistoryTransfer(StoredImage image, byte[] bytes) {}

    private static final class UploadSession {
        private final UUID id;
        private final String displayName;
        private final int requestedCategory;
        private final int totalBytes;
        private final byte[][] chunks;
        private final long expiresAtMillis;
        private int receivedChunks;

        private UploadSession(UUID id, String displayName, int requestedCategory,
                              int totalBytes, int chunkCount, long expiresAtMillis) {
            this.id = id;
            this.displayName = displayName;
            this.requestedCategory = requestedCategory;
            this.totalBytes = totalBytes;
            this.chunks = new byte[chunkCount][];
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}
