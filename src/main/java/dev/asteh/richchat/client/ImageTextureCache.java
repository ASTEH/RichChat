package dev.asteh.richchat.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Ограниченный LRU-кэш клиентских DynamicTexture. */
public final class ImageTextureCache {
    private static final int MAX_TEXTURES = 48;
    private static final Map<String, TextureEntry> CACHE = new LinkedHashMap<>(16, 0.75F, true);

    private ImageTextureCache() {}

    public static synchronized TextureEntry get(String key, Path path) throws IOException {
        TextureEntry cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        BufferedImage buffered = ImageIO.read(path.toFile());
        if (buffered == null) {
            throw new IOException("ImageIO could not decode " + path.getFileName());
        }

        int width = buffered.getWidth();
        int height = buffered.getHeight();
        if (width <= 0 || height <= 0 || width > 8192 || height > 8192) {
            throw new IOException("Invalid image dimensions for texture upload.");
        }

        NativeImage nativeImage = new NativeImage(width, height, false);
        try {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = buffered.getRGB(x, y);
                    int alpha = (argb >>> 24) & 0xFF;
                    int red = (argb >>> 16) & 0xFF;
                    int green = (argb >>> 8) & 0xFF;
                    int blue = argb & 0xFF;
                    // NativeImage использует формат ABGR в int-представлении.
                    int abgr = (alpha << 24) | (blue << 16) | (green << 8) | red;
                    nativeImage.setPixelRGBA(x, y, abgr);
                }
            }

            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                    "richchat", "dynamic/" + safeTextureKey(key));
            DynamicTexture dynamicTexture = new DynamicTexture(nativeImage);
            Minecraft.getInstance().getTextureManager().register(location, dynamicTexture);
            TextureEntry entry = new TextureEntry(location, width, height);
            CACHE.put(key, entry);
            trimCache();
            return entry;
        } catch (RuntimeException exception) {
            nativeImage.close();
            throw exception;
        }
    }

    public static synchronized void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        for (TextureEntry entry : CACHE.values()) {
            minecraft.getTextureManager().release(entry.location());
        }
        CACHE.clear();
    }

    private static void trimCache() {
        while (CACHE.size() > MAX_TEXTURES) {
            String eldestKey = CACHE.keySet().iterator().next();
            TextureEntry eldest = CACHE.remove(eldestKey);
            Minecraft.getInstance().getTextureManager().release(eldest.location());
        }
    }

    private static String safeTextureKey(String key) {
        return key.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    public record TextureEntry(ResourceLocation location, int width, int height) {}
}
