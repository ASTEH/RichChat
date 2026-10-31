package dev.asteh.richchat.network;

/** Ограничения размера сетевых фрагментов для протокола 1.21.1. */
public final class ImageTransferConstants {
    // C2S payload обязан быть меньше 32 KiB с учётом заголовков пакета.
    public static final int UPLOAD_CHUNK_BYTES = 24 * 1024;
    // S2C payload ограничен 1 MiB; 48 KiB оставляет большой запас.
    public static final int DOWNLOAD_CHUNK_BYTES = 48 * 1024;

    private ImageTransferConstants() {}
}
