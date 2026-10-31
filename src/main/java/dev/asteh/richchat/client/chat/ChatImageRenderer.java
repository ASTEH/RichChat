package dev.asteh.richchat.client.chat;

import dev.asteh.richchat.client.ImageTextureCache;
import dev.asteh.richchat.client.gui.ImagePreviewOverlay;
import dev.asteh.richchat.client.storage.LocalImageLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Рисует эскизы поверх зарезервированной области строки RichChat. */
public final class ChatImageRenderer {
    private static final int VANILLA_LINE_HEIGHT = 9;
    private static final int CHAT_BOTTOM_OFFSET = 40;
    private static final int IMAGE_SIZE = 63;

    private static final List<ImageHitbox> HITBOXES = new ArrayList<>();

    private ChatImageRenderer() {}

    public static void render(
            ChatComponent chat,
            GuiGraphics graphics,
            List<GuiMessage.Line> lines,
            int scrollPosition,
            int tickCount,
            boolean focused
    ) {
        HITBOXES.clear();

        Minecraft minecraft = Minecraft.getInstance();
        double scale = minecraft.options.chat().getScale();
        if (scale <= 0.0D || lines.isEmpty()) {
            return;
        }

        int end = Math.min(lines.size(), scrollPosition + chat.getLinesPerPage());
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();

        for (int index = Math.max(0, scrollPosition); index < end; index++) {
            GuiMessage.Line line = lines.get(index);
            String imageId = imageIdFrom(line);
            if (imageId == null) {
                continue;
            }

            if (!focused && tickCount - line.addedTime() >= 200) {
                continue;
            }

            Path path = LocalImageLibrary.findCachedPath(imageId);
            if (path == null) {
                continue;
            }

            try {
                ImageTextureCache.TextureEntry texture = ImageTextureCache.get(imageId, path);
                double row = index - scrollPosition;
                int bottom = (int) Math.round(screenHeight - CHAT_BOTTOM_OFFSET * scale
                        - row * VANILLA_LINE_HEIGHT * scale);
                int targetSize = (int) Math.round(IMAGE_SIZE * scale);
                double fit = Math.min((double) targetSize / texture.width(),
                        (double) targetSize / texture.height());
                int drawWidth = Math.max(1, (int) Math.round(texture.width() * fit));
                int drawHeight = Math.max(1, (int) Math.round(texture.height() * fit));
                int x = (int) Math.round(4.0D * scale);
                int y = bottom - drawHeight - (int) Math.round(1.0D * scale);

                graphics.fill(x - 1, y - 1, x + drawWidth + 1, y + drawHeight + 1, 0xFF202020);
                graphics.blit(texture.location(), x, y, 0.0F, 0.0F,
                        drawWidth, drawHeight, texture.width(), texture.height());

                HITBOXES.add(new ImageHitbox(imageId, path, x, y, drawWidth, drawHeight));
            } catch (IOException | RuntimeException exception) {
                dev.asteh.richchat.RichChat.LOGGER.warn("Could not render RichChat image {}", imageId, exception);
            }
        }
    }

    public static boolean mouseClicked(double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        for (int i = HITBOXES.size() - 1; i >= 0; i--) {
            ImageHitbox hitbox = HITBOXES.get(i);
            if (mouseX >= hitbox.x && mouseX <= hitbox.x + hitbox.width
                    && mouseY >= hitbox.y && mouseY <= hitbox.y + hitbox.height) {
                minecraft.setScreen(new ImagePreviewOverlay(minecraft.screen, hitbox.path, hitbox.imageId));
                return true;
            }
        }
        return false;
    }

    private static String imageIdFrom(GuiMessage.Line line) {
        if (line.tag() == null || line.tag().logTag() == null) {
            return null;
        }
        String logTag = line.tag().logTag();
        if (!logTag.startsWith("richchat:")) {
            return null;
        }
        return logTag.substring("richchat:".length());
    }

    private record ImageHitbox(String imageId, Path path, int x, int y, int width, int height) {}
}
