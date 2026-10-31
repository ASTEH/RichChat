package dev.asteh.richchat.server;

/** Метаданные изображения, которые сохраняются в истории мира. */
public record StoredImage(
        String id,
        String senderName,
        String displayName,
        String extension,
        int category,
        int width,
        int height,
        int sizeBytes,
        long createdAtMillis
) {
}
