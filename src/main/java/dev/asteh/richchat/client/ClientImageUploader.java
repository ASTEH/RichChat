package dev.asteh.richchat.client;

import dev.asteh.richchat.network.C2SUploadImageChunkPayload;
import dev.asteh.richchat.network.C2SUploadImageStartPayload;
import dev.asteh.richchat.network.ImageTransferConstants;
import dev.asteh.richchat.server.ImageValidator;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Клиентская отправка локального файла серверу. Вызывается из будущей панели выбора. */
public final class ClientImageUploader {
    private static final long MAX_LOCAL_UPLOAD_BYTES = 8L * 1024L * 1024L;

    private ClientImageUploader() {}

    public static CompletableFuture<Void> upload(Path file, int requestedCategory) {
        Minecraft minecraft = Minecraft.getInstance();
        CompletableFuture<Void> result = new CompletableFuture<>();

        CompletableFuture.supplyAsync(() -> prepare(file, requestedCategory))
                .whenComplete((prepared, error) -> {
                    if (error != null) {
                        result.completeExceptionally(unwrap(error));
                        return;
                    }

                    minecraft.execute(() -> {
                        try {
                            if (minecraft.getConnection() == null) {
                                throw new IllegalStateException("You must be connected to a server to send an image.");
                            }

                            PacketDistributor.sendToServer(new C2SUploadImageStartPayload(
                                    prepared.uploadId().toString(),
                                    prepared.fileName(),
                                    prepared.requestedCategory(),
                                    prepared.bytes().length,
                                    prepared.width(),
                                    prepared.height(),
                                    prepared.chunkCount()
                            ));

                            for (int index = 0; index < prepared.chunkCount(); index++) {
                                int start = index * ImageTransferConstants.UPLOAD_CHUNK_BYTES;
                                int end = Math.min(prepared.bytes().length,
                                        start + ImageTransferConstants.UPLOAD_CHUNK_BYTES);
                                PacketDistributor.sendToServer(new C2SUploadImageChunkPayload(
                                        prepared.uploadId().toString(),
                                        index,
                                        Arrays.copyOfRange(prepared.bytes(), start, end)
                                ));
                            }
                            result.complete(null);
                        } catch (RuntimeException exception) {
                            result.completeExceptionally(exception);
                        }
                    });
                });

        return result;
    }

    private static PreparedUpload prepare(Path file, int requestedCategory) {
        try {
            if (requestedCategory < 0 || requestedCategory > 2) {
                throw new IOException("Unknown image category.");
            }

            long size = Files.size(file);
            if (size <= 0 || size > MAX_LOCAL_UPLOAD_BYTES) {
                throw new IOException("The local upload limit is 8 MiB.");
            }

            byte[] bytes = Files.readAllBytes(file);
            ImageValidator.Validation validation = ImageValidator.validate(bytes, 8192, 8192);
            if (!validation.accepted()) {
                throw new IOException(validation.reason());
            }
            if (requestedCategory == 2
                    && (validation.width() > 128 || validation.height() > 128)) {
                throw new IOException("This file is too large for a sticker. Choose photo or GIF instead.");
            }

            String name = file.getFileName() == null ? "image" : file.getFileName().toString();
            if (name.length() > 128) {
                name = name.substring(0, 128);
            }

            int chunks = (bytes.length + ImageTransferConstants.UPLOAD_CHUNK_BYTES - 1)
                    / ImageTransferConstants.UPLOAD_CHUNK_BYTES;
            return new PreparedUpload(
                    UUID.randomUUID(), name, requestedCategory, bytes,
                    validation.width(), validation.height(), chunks
            );
        } catch (IOException exception) {
            throw new java.util.concurrent.CompletionException(exception);
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof java.util.concurrent.CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    private record PreparedUpload(
            UUID uploadId,
            String fileName,
            int requestedCategory,
            byte[] bytes,
            int width,
            int height,
            int chunkCount
    ) {}
}
