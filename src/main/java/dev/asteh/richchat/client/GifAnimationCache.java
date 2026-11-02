package dev.asteh.richchat.client;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decodes animated GIFs into fully composited frames.
 *
 * The decoded-frame cache is deliberately bounded independently from the GPU texture cache:
 * malformed or high-resolution GIFs must not be able to consume unbounded client memory.
 */
public final class GifAnimationCache {
    private static final int MAX_FRAMES = 100;
    private static final int MAX_DIMENSION = 8192;
    private static final long MAX_PIXELS_PER_GIF = 16_777_216L;
    private static final long MAX_TOTAL_CACHE_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_CACHED_GIFS = 8;

    private static final Map<String, GifAnimation> CACHE = new LinkedHashMap<>(8, 0.75F, true);
    private static long cachedBytes;

    private GifAnimationCache() {}

    /**
     * Returns the frame that should be visible at the supplied time.
     * A null result means that the file is not an animated GIF or could not fit the decode limits.
     */
    public static synchronized Frame getFrame(String key, Path path, long nowMillis) throws IOException {
        GifAnimation animation = CACHE.get(key);
        if (animation == null) {
            animation = decode(path, nowMillis);
            if (animation == null) {
                return null;
            }
            if (animation.memoryBytes > MAX_TOTAL_CACHE_BYTES) {
                return null;
            }

            CACHE.put(key, animation);
            cachedBytes += animation.memoryBytes;
            trimCache(key);
        }

        return animation.frameAt(nowMillis);
    }

    public static synchronized void clear() {
        CACHE.clear();
        cachedBytes = 0L;
    }

    private static void trimCache(String protectedKey) {
        Iterator<Map.Entry<String, GifAnimation>> iterator = CACHE.entrySet().iterator();
        while ((CACHE.size() > MAX_CACHED_GIFS || cachedBytes > MAX_TOTAL_CACHE_BYTES)
                && iterator.hasNext()) {
            Map.Entry<String, GifAnimation> entry = iterator.next();
            if (entry.getKey().equals(protectedKey) && CACHE.size() > 1) {
                continue;
            }
            cachedBytes -= entry.getValue().memoryBytes;
            iterator.remove();
        }
    }

    private static GifAnimation decode(Path path, long startTimeMillis) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null) {
                throw new IOException("Could not open GIF image stream.");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false, false);
                if (!"gif".equalsIgnoreCase(reader.getFormatName())) {
                    return null;
                }

                int frameCount = reader.getNumImages(true);
                if (frameCount < 2 || frameCount > MAX_FRAMES) {
                    return null;
                }

                IIOMetadata streamMetadata = reader.getStreamMetadata();
                Node streamRoot = streamMetadata == null
                        ? null : streamMetadata.getAsTree("javax_imageio_gif_stream_1.0");
                Node logicalScreen = child(streamRoot, "LogicalScreenDescriptor");

                int screenWidth = intAttribute(logicalScreen, "logicalScreenWidth", reader.getWidth(0));
                int screenHeight = intAttribute(logicalScreen, "logicalScreenHeight", reader.getHeight(0));
                if (screenWidth <= 0 || screenHeight <= 0
                        || screenWidth > MAX_DIMENSION || screenHeight > MAX_DIMENSION) {
                    return null;
                }

                long totalPixels = (long) screenWidth * screenHeight * frameCount;
                if (totalPixels > MAX_PIXELS_PER_GIF) {
                    return null;
                }

                BufferedImage canvas = new BufferedImage(
                        screenWidth, screenHeight, BufferedImage.TYPE_INT_ARGB);
                List<BufferedImage> frames = new ArrayList<>(frameCount);
                List<Integer> delays = new ArrayList<>(frameCount);

                String previousDisposal = "none";
                int previousLeft = 0;
                int previousTop = 0;
                int previousWidth = 0;
                int previousHeight = 0;
                BufferedImage restoreCanvas = null;
                long totalDuration = 0L;

                for (int index = 0; index < frameCount; index++) {
                    if ("restoreToBackgroundColor".equals(previousDisposal)) {
                        clearRect(canvas, previousLeft, previousTop, previousWidth, previousHeight);
                    } else if ("restoreToPrevious".equals(previousDisposal) && restoreCanvas != null) {
                        canvas = copy(restoreCanvas);
                    }

                    IIOMetadata frameMetadata = reader.getImageMetadata(index);
                    Node frameRoot = frameMetadata.getAsTree("javax_imageio_gif_image_1.0");
                    Node descriptor = child(frameRoot, "ImageDescriptor");
                    Node control = child(frameRoot, "GraphicControlExtension");

                    int left = intAttribute(descriptor, "imageLeftPosition", 0);
                    int top = intAttribute(descriptor, "imageTopPosition", 0);
                    int frameWidth = intAttribute(descriptor, "imageWidth", reader.getWidth(index));
                    int frameHeight = intAttribute(descriptor, "imageHeight", reader.getHeight(index));
                    if (left < 0 || top < 0 || frameWidth <= 0 || frameHeight <= 0
                            || left + frameWidth > screenWidth || top + frameHeight > screenHeight) {
                        throw new IOException("GIF frame lies outside the logical screen.");
                    }

                    String disposal = stringAttribute(control, "disposalMethod", "none");
                    restoreCanvas = "restoreToPrevious".equals(disposal) ? copy(canvas) : null;

                    BufferedImage rawFrame = reader.read(index);
                    if (rawFrame == null) {
                        throw new IOException("GIF decoder returned an empty frame.");
                    }

                    Graphics2D graphics = canvas.createGraphics();
                    try {
                        graphics.setComposite(AlphaComposite.SrcOver);
                        graphics.drawImage(rawFrame, left, top, null);
                    } finally {
                        graphics.dispose();
                    }

                    frames.add(copy(canvas));

                    int delayCentiseconds = intAttribute(control, "delayTime", 10);
                    int delayMillis = Math.max(20, Math.min(10_000, delayCentiseconds * 10));
                    delays.add(delayMillis);
                    totalDuration += delayMillis;

                    previousDisposal = disposal;
                    previousLeft = left;
                    previousTop = top;
                    previousWidth = frameWidth;
                    previousHeight = frameHeight;
                }

                long memoryBytes = totalPixels * 4L;
                return new GifAnimation(frames, delays, totalDuration, memoryBytes, startTimeMillis);
            } finally {
                reader.dispose();
            }
        } catch (RuntimeException exception) {
            throw new IOException("Could not decode animated GIF.", exception);
        }
    }

    private static void clearRect(BufferedImage image, int x, int y, int width, int height) {
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Clear);
            graphics.fillRect(x, y, width, height);
        } finally {
            graphics.dispose();
        }
    }

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage target = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static Node child(Node parent, String name) {
        if (parent == null) {
            return null;
        }
        for (Node current = parent.getFirstChild(); current != null; current = current.getNextSibling()) {
            if (name.equals(current.getNodeName())) {
                return current;
            }
        }
        return null;
    }

    private static String stringAttribute(Node node, String name, String fallback) {
        if (node == null) {
            return fallback;
        }
        NamedNodeMap attributes = node.getAttributes();
        Node value = attributes == null ? null : attributes.getNamedItem(name);
        return value == null ? fallback : value.getNodeValue();
    }

    private static int intAttribute(Node node, String name, int fallback) {
        try {
            return Integer.parseInt(stringAttribute(node, name, Integer.toString(fallback)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public record Frame(BufferedImage image, int index) {}

    private static final class GifAnimation {
        private final List<BufferedImage> frames;
        private final List<Integer> delays;
        private final long totalDuration;
        private final long memoryBytes;
        private final long startTimeMillis;

        private GifAnimation(List<BufferedImage> frames, List<Integer> delays,
                            long totalDuration, long memoryBytes, long startTimeMillis) {
            this.frames = List.copyOf(frames);
            this.delays = List.copyOf(delays);
            this.totalDuration = Math.max(1L, totalDuration);
            this.memoryBytes = memoryBytes;
            this.startTimeMillis = startTimeMillis;
        }

        private Frame frameAt(long nowMillis) {
            long elapsed = Math.floorMod(nowMillis - startTimeMillis, totalDuration);
            for (int index = 0; index < delays.size(); index++) {
                int delay = delays.get(index);
                if (elapsed < delay) {
                    return new Frame(frames.get(index), index);
                }
                elapsed -= delay;
            }
            return new Frame(frames.get(0), 0);
        }
    }
}
