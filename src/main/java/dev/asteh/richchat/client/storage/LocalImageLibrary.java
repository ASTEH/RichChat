package dev.asteh.richchat.client.storage;

import dev.asteh.richchat.server.ImageValidator;
import dev.asteh.richchat.server.StoredImage;
import net.minecraft.client.Minecraft;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Локальная библиотека изображений и кэш полученных с сервера файлов. */
public final class LocalImageLibrary {
    private static final long MAX_LOCAL_FILE_BYTES = 8L * 1024L * 1024L;
    private static final int MAX_LOCAL_DIMENSION = 8192;
    private static final int MAX_STICKER_DIMENSION = 128;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type INDEX_TYPE = new TypeToken<List<LocalImageEntry>>() {}.getType();

    private LocalImageLibrary() {}

    public static Path root() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("richchat").resolve("images");
    }

    public static Path folderForCategory(int category) {
        return switch (category) {
            case 0 -> root().resolve("photos");
            case 1 -> root().resolve("gifs");
            case 2 -> root().resolve("stickers");
            default -> throw new IllegalArgumentException("Unknown RichChat category: " + category);
        };
    }

    public static void ensureDirectories() throws IOException {
        Files.createDirectories(folderForCategory(0));
        Files.createDirectories(folderForCategory(1));
        Files.createDirectories(folderForCategory(2));
        Files.createDirectories(root().resolve("cache"));
    }

    public static synchronized List<LocalImageEntry> entries(int category) throws IOException {
        ensureDirectories();
        return loadIndex().stream()
                .filter(entry -> entry.category() == category)
                .sorted(Comparator.comparingLong(LocalImageEntry::createdAtMillis).reversed())
                .toList();
    }

    public static synchronized LocalImageEntry importImage(Path source, int requestedCategory) throws IOException {
        ensureDirectories();
        if (requestedCategory < 0 || requestedCategory > 2) {
            throw new IOException("Unknown image category.");
        }

        long sourceSize = Files.size(source);
        if (sourceSize <= 0 || sourceSize > MAX_LOCAL_FILE_BYTES) {
            throw new IOException("The local image must be between 1 byte and 8 MiB.");
        }

        byte[] bytes = Files.readAllBytes(source);
        ImageValidator.Validation validation = ImageValidator.validate(
                bytes, MAX_LOCAL_DIMENSION, MAX_LOCAL_DIMENSION);
        if (!validation.accepted()) {
            throw new IOException(validation.reason());
        }

        int actualCategory;
        if (requestedCategory == 2) {
            if (validation.width() > MAX_STICKER_DIMENSION
                    || validation.height() > MAX_STICKER_DIMENSION) {
                throw new IOException("Sticker is larger than 128×128. Import it as a photo or GIF.");
            }
            actualCategory = 2;
        } else {
            actualCategory = validation.animated() ? 1 : 0;
        }

        String extension = extensionFor(validation.format());
        String id = UUID.randomUUID().toString();
        String displayName = sanitizeName(source.getFileName() == null
                ? "image" : source.getFileName().toString());
        Path destination = folderForCategory(actualCategory).resolve(id + "." + extension);
        writeAtomically(destination, bytes);

        LocalImageEntry entry = new LocalImageEntry(
                id, displayName, actualCategory, extension, bytes.length,
                System.currentTimeMillis(), false, 0L
        );

        List<LocalImageEntry> index = loadIndex();
        index.add(entry);
        saveIndex(index);
        return entry;
    }

    public static synchronized void delete(String id) throws IOException {
        List<LocalImageEntry> index = loadIndex();
        LocalImageEntry match = index.stream().filter(entry -> entry.id().equals(id)).findFirst().orElse(null);
        if (match == null) {
            return;
        }
        Path folder = folderForCategory(match.category()).normalize();
        Path file = folder.resolve(match.id() + "." + match.extension()).normalize();
        if (!file.startsWith(folder)) {
            throw new IOException("Invalid local library path.");
        }
        Files.deleteIfExists(file);
        index.removeIf(entry -> entry.id().equals(id));
        saveIndex(index);
    }

    public static synchronized void setFavorite(String id, boolean favorite) throws IOException {
        updateEntry(id, current -> new LocalImageEntry(
                current.id(), current.fileName(), current.category(), current.extension(),
                current.sizeBytes(), current.createdAtMillis(), favorite, current.lastUsedAtMillis()
        ));
    }

    public static synchronized void markUsed(String id) throws IOException {
        updateEntry(id, current -> new LocalImageEntry(
                current.id(), current.fileName(), current.category(), current.extension(),
                current.sizeBytes(), current.createdAtMillis(), current.favorite(), System.currentTimeMillis()
        ));
    }

    /** Возвращает безопасный путь к исходнику из пользовательской библиотеки. */
    public static synchronized Path localPath(LocalImageEntry entry) {
        UUID.fromString(entry.id());
        return folderForCategory(entry.category()).resolve(entry.id() + "." + entry.extension()).normalize();
    }

    /** Ищет локально сохранённый файл, полученный от сервера. */
    public static synchronized Path findCachedPath(String id) {
        try {
            UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            return null;
        }

        Path cacheRoot = root().resolve("cache").normalize();
        for (String category : List.of("photos", "gifs", "stickers")) {
            for (String extension : List.of("png", "jpg", "gif")) {
                Path candidate = cacheRoot.resolve(category).resolve(id + "." + extension).normalize();
                if (candidate.startsWith(cacheRoot) && Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    public static synchronized Path saveReceived(StoredImage image, byte[] bytes) throws IOException {
        ensureDirectories();
        String categoryFolder = switch (image.category()) {
            case 0 -> "photos";
            case 1 -> "gifs";
            case 2 -> "stickers";
            default -> throw new IOException("Server sent an unknown image category.");
        };
        String extension = switch (image.extension()) {
            case "png", "jpg", "gif" -> image.extension();
            default -> throw new IOException("Server sent an unsupported image extension.");
        };

        Path cacheRoot = root().resolve("cache").resolve(categoryFolder).normalize();
        Files.createDirectories(cacheRoot);
        Path destination = cacheRoot.resolve(image.id() + "." + extension).normalize();
        if (!destination.startsWith(cacheRoot)) {
            throw new IOException("Received image path escapes cache directory.");
        }
        writeAtomically(destination, bytes);
        return destination;
    }

    private static void updateEntry(String id,
                                    java.util.function.UnaryOperator<LocalImageEntry> updater) throws IOException {
        List<LocalImageEntry> index = loadIndex();
        for (int i = 0; i < index.size(); i++) {
            if (index.get(i).id().equals(id)) {
                index.set(i, updater.apply(index.get(i)));
                saveIndex(index);
                return;
            }
        }
    }

    private static List<LocalImageEntry> loadIndex() throws IOException {
        Path indexPath = root().resolve("index.json");
        if (!Files.exists(indexPath)) {
            return new ArrayList<>();
        }

        try (var reader = Files.newBufferedReader(indexPath)) {
            List<LocalImageEntry> entries = GSON.fromJson(reader, INDEX_TYPE);
            return entries == null ? new ArrayList<>() : new ArrayList<>(entries);
        } catch (RuntimeException exception) {
            Path broken = root().resolve("index.json.broken");
            Files.move(indexPath, broken, StandardCopyOption.REPLACE_EXISTING);
            return new ArrayList<>();
        }
    }

    private static void saveIndex(List<LocalImageEntry> entries) throws IOException {
        Path path = root().resolve("index.json");
        Path temporary = root().resolve("index.json.tmp");
        Files.writeString(temporary, GSON.toJson(entries),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void writeAtomically(Path destination, byte[] bytes) throws IOException {
        Files.createDirectories(destination.getParent());
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        Files.write(temporary, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        try {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String extensionFor(String format) throws IOException {
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "png" -> "png";
            case "jpeg" -> "jpg";
            case "gif" -> "gif";
            default -> throw new IOException("Unsupported image format.");
        };
    }

    private static String sanitizeName(String name) {
        String safe = name.replace('\\', '_').replace('/', '_')
                .replaceAll("[^\\p{L}\\p{N}._ -]", "_").trim();
        if (safe.length() > 64) {
            safe = safe.substring(0, 64);
        }
        return safe.isBlank() ? "image" : safe;
    }

    public record LocalImageEntry(
            String id,
            String fileName,
            int category,
            String extension,
            int sizeBytes,
            long createdAtMillis,
            boolean favorite,
            long lastUsedAtMillis
    ) {}
}
