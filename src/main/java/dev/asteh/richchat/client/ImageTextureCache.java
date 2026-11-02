package dev.asteh.richchat.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Ограниченный LRU-кэш клиентских DynamicTexture с обновлением кадров анимации. */
public final class ImageTextureCache {
    private static final int MAX_TEXTURES = 48;
    private static final Map<String, CachedTexture> CACHE = new LinkedHashMap<>(16, 0.75F, true);

    private ImageTextureCache() {}

    public static synchronized TextureEntry get(String key, Path path) throws IOException {
        CachedTexture cached = CACHE.get(key);
        if (cached != null) {
            return cached.entry;
        }

        BufferedImage buffered = ImageIO.read(path.toFile());
        if (buffered == null) {
            throw new IOException("ImageIO could not decode " + path.getFileName());
        }
        return create(key, buffered, -1).entry;
    }

    /** Обновляет существующую GPU-текстуру, только если сменился кадр GIF. */
    public static synchronized TextureEntry update(String key, int frameIndex, BufferedImage frame)
            throws IOException {
        CachedTexture cached = CACHE.get(key);
        if (cached == null || cached.entry.width() != frame.getWidth()
                || cached.entry.height() != frame.getHeight()) {
            if (cached != null) {
                release(key, cached);
            }
            return create(key, frame, frameIndex).entry;
        }

        if (cached.uploadedFrameIndex != frameIndex) {
            writePixels(cached.pixels, frame);
            cached.texture.upload();
            cached.uploadedFrameIndex = frameIndex;
        }
        return cached.entry;
    }

    public static synchronized void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        for (CachedTexture entry : CACHE.values()) {
            minecraft.getTextureManager().release(entry.entry.location());
        }
        CACHE.clear();
    }

    private static CachedTexture create(String key, BufferedImage buffered, int frameIndex)
            throws IOException {
        int width = buffered.getWidth();
        int height = buffered.getHeight();
        if (width <= 0 || height <= 0 || width > 8192 || height > 8192) {
            throw new IOException("Invalid image dimensions for texture upload.");
        }

        NativeImage nativeImage = new NativeImage(width, height, false);
        try {
            writePixels(nativeImage, buffered);
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                    "richchat", "dynamic/" + safeTextureKey(key));
            DynamicTexture dynamicTexture = new DynamicTexture(nativeImage);
            Minecraft.getInstance().getTextureManager().register(location, dynamicTexture);
            TextureEntry entry = new TextureEntry(location, width, height);
            CachedTexture cached = new CachedTexture(entry, nativeImage, dynamicTexture, frameIndex);
            CACHE.put(key, cached);
            trimCache();
            return cached;
        } catch (RuntimeException exception) {
            nativeImage.close();
            throw exception;
        }
    }

    private static void writePixels(NativeImage target, BufferedImage source) {
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                // NativeImage uses ABGR ordering in its integer pixel representation.
                int abgr = (alpha << 24) | (blue << 16) | (green << 8) | red;
                target.setPixelRGBA(x, y, abgr);
            }
        }
    }

    private static void trimCache() {
        Iterator<Map.Entry<String, CachedTexture>> iterator = CACHE.entrySet().iterator();
        while (CACHE.size() > MAX_TEXTURES && iterator.hasNext()) {
            Map.Entry<String, CachedTexture> entry = iterator.next();
            Minecraft.getInstance().getTextureManager().release(entry.getValue().entry.location());
            iterator.remove();
        }
    }

    private static void release(String key, CachedTexture cached) {
        CACHE.remove(key);
        Minecraft.getInstance().getTextureManager().release(cached.entry.location());
    }

    private static String safeTextureKey(String key) {
        return key.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    public record TextureEntry(ResourceLocation location, int width, int height) {}

    private static final class CachedTexture {
        private final TextureEntry entry;
        private final NativeImage pixels;
        private final DynamicTexture texture;
        private int uploadedFrameIndex;

        private CachedTexture(TextureEntry entry, NativeImage pixels,
                              DynamicTexture texture, int uploadedFrameIndex) {
            this.entry = entry;
            this.pixels = pixels;
            this.texture = texture;
            this.uploadedFrameIndex = uploadedFrameIndex;
        }
    }
}
