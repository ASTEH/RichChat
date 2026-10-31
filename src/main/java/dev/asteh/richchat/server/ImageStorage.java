package dev.asteh.richchat.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Хранит байты в папке текущего мира, а не в общей папке клиента/сервера. */
public final class ImageStorage {
    private ImageStorage() {}

    public static Path store(MinecraftServer server, String imageId, String extension, byte[] bytes)
            throws IOException {
        Path worldRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path imageDirectory = worldRoot.resolve("richchat").resolve("images").normalize();
        if (!imageDirectory.startsWith(worldRoot)) {
            throw new IOException("Resolved image directory escapes world root.");
        }

        Files.createDirectories(imageDirectory);
        Path target = imageDirectory.resolve(imageId + "." + extension).normalize();
        if (!target.startsWith(imageDirectory)) {
            throw new IOException("Resolved image path escapes image directory.");
        }

        Path temporary = imageDirectory.resolve(imageId + ".part");
        Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try {
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target);
            }
        } catch (IOException exception) {
            Files.deleteIfExists(temporary);
            throw exception;
        }
        return target;
    }

    public static Path resolve(MinecraftServer server, StoredImage image) throws IOException {
        String extension = switch (image.extension()) {
            case "png", "jpg", "gif" -> image.extension();
            default -> throw new IOException("Unexpected stored image extension.");
        };

        Path worldRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path imageDirectory = worldRoot.resolve("richchat").resolve("images").normalize();
        Path path = imageDirectory.resolve(image.id() + "." + extension).normalize();

        if (!path.startsWith(imageDirectory) || !Files.isRegularFile(path)) {
            throw new IOException("Stored image does not exist.");
        }
        return path;
    }
}
