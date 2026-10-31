package dev.asteh.richchat.server;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

/** Проверяет сигнатуру и декодируемость PNG/JPEG/GIF до записи на диск. */
public final class ImageValidator {
    public static final int MAX_GIF_FRAMES = 100;

    private ImageValidator() {}

    public static Validation validate(byte[] bytes, int maxWidth, int maxHeight) {
        if (bytes == null || bytes.length < 12) {
            return Validation.rejected("Image file is empty or truncated.");
        }

        String signature = detectSignature(bytes);
        if (signature == null) {
            return Validation.rejected("Only PNG, JPEG and GIF files are accepted.");
        }

        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                return Validation.rejected("Could not open image data.");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return Validation.rejected("No installed ImageIO decoder accepts this image.");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!matchesSignature(signature, format)) {
                    return Validation.rejected("Image signature and decoded format do not match.");
                }

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > maxWidth || height > maxHeight) {
                    return Validation.rejected("Image dimensions exceed the server limit.");
                }

                int frameCount = 1;
                if ("gif".equals(signature)) {
                    frameCount = reader.getNumImages(true);
                    if (frameCount < 1 || frameCount > MAX_GIF_FRAMES) {
                        return Validation.rejected("GIF frame count is outside the accepted range.");
                    }

                    for (int frame = 0; frame < frameCount; frame++) {
                        int frameWidth = reader.getWidth(frame);
                        int frameHeight = reader.getHeight(frame);
                        if (frameWidth <= 0 || frameHeight <= 0
                                || frameWidth > maxWidth || frameHeight > maxHeight) {
                            return Validation.rejected("A GIF frame exceeds the server dimension limit.");
                        }
                    }
                }

                BufferedImage firstFrame = reader.read(0);
                if (firstFrame == null) {
                    return Validation.rejected("Image decoder returned no pixels.");
                }

                return Validation.accepted(signature, width, height, frameCount > 1);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            return Validation.rejected("Image decoding failed: " + exception.getClass().getSimpleName());
        }
    }

    private static String detectSignature(byte[] bytes) {
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47
                && bytes[4] == 0x0D && bytes[5] == 0x0A
                && bytes[6] == 0x1A && bytes[7] == 0x0A) {
            return "png";
        }

        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return "jpeg";
        }

        if (bytes.length >= 6) {
            String header = new String(bytes, 0, 6, java.nio.charset.StandardCharsets.US_ASCII);
            if ("GIF87a".equals(header) || "GIF89a".equals(header)) {
                return "gif";
            }
        }
        return null;
    }

    private static boolean matchesSignature(String signature, String readerFormat) {
        return switch (signature) {
            case "png" -> "png".equals(readerFormat);
            case "jpeg" -> "jpeg".equals(readerFormat) || "jpg".equals(readerFormat);
            case "gif" -> "gif".equals(readerFormat);
            default -> false;
        };
    }

    public record Validation(boolean accepted, String reason, String format,
                             int width, int height, boolean animated) {
        public static Validation accepted(String format, int width, int height, boolean animated) {
            return new Validation(true, "", format, width, height, animated);
        }

        public static Validation rejected(String reason) {
            return new Validation(false, reason, "", 0, 0, false);
        }
    }
}
