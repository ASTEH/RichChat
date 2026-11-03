package dev.asteh.richchat.client.gui;

import dev.asteh.richchat.client.GifAnimationCache;
import dev.asteh.richchat.client.ImageTextureCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;

/** Полноразмерный просмотр изображения в общей визуальной теме RichChat. */
public final class ImagePreviewOverlay extends Screen {
    private static final int SHELL = 0xF0181B1B;
    private static final int PANEL = 0xF0222625;
    private static final int BORDER = 0xFF4A514B;
    private static final int ACCENT = 0xFFE0A15D;
    private static final int TEXT = 0xFFF0EEE7;
    private static final int MUTED = 0xFFB0B5AC;
    private static final int RED = 0xFFE58C7F;

    private final Screen parent;
    private final Path imagePath;
    private final String imageId;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int closeX;
    private int closeY;
    private int closeW;

    public ImagePreviewOverlay(Screen parent, Path imagePath, String imageId) {
        super(Component.translatable("richchat.preview.title"));
        this.parent = parent;
        this.imagePath = imagePath;
        this.imageId = imageId;
    }

    @Override
    protected void init() {
        panelW = Math.max(1, Math.min(width - 24, 820));
        panelH = Math.max(1, Math.min(height - 24, 560));
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        closeW = 84;
        closeX = panelX + panelW - closeW - 12;
        closeY = panelY + panelH - 37;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0xD0101010);

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, SHELL);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, ACCENT);
        graphics.fill(panelX, panelY + panelH - 1,
                panelX + panelW, panelY + panelH, BORDER);

        graphics.drawString(font, Component.translatable("richchat.ui.kicker"),
                panelX + 12, panelY + 10, ACCENT);
        graphics.drawString(font, title, panelX + 12, panelY + 23, TEXT);

        String fileName = imagePath.getFileName() == null
                ? imageId : imagePath.getFileName().toString();
        graphics.drawString(font, fileName, panelX + 12, panelY + 40, MUTED);

        int imageAreaX = panelX + 18;
        int imageAreaY = panelY + 60;
        int imageAreaW = Math.max(24, panelW - 36);
        int imageAreaH = Math.max(24, panelH - 112);
        graphics.fill(imageAreaX, imageAreaY,
                imageAreaX + imageAreaW, imageAreaY + imageAreaH, PANEL);
        drawCheckerboard(graphics, imageAreaX + 3, imageAreaY + 3,
                imageAreaW - 6, imageAreaH - 6);

        try {
            ImageTextureCache.TextureEntry texture;
            if (imagePath.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".gif")) {
                GifAnimationCache.Frame frame = GifAnimationCache.getFrame(
                        imageId, imagePath, System.currentTimeMillis());
                texture = frame == null
                        ? ImageTextureCache.get(imageId, imagePath)
                        : ImageTextureCache.update(imageId, frame.index(), frame.image());
            } else {
                texture = ImageTextureCache.get(imageId, imagePath);
            }

            int maxWidth = Math.max(24, imageAreaW - 20);
            int maxHeight = Math.max(24, imageAreaH - 20);
            double fit = Math.min((double) maxWidth / texture.width(),
                    (double) maxHeight / texture.height());
            int drawWidth = Math.max(1, (int) Math.round(texture.width() * fit));
            int drawHeight = Math.max(1, (int) Math.round(texture.height() * fit));
            int x = imageAreaX + (imageAreaW - drawWidth) / 2;
            int y = imageAreaY + (imageAreaH - drawHeight) / 2;
            graphics.fill(x - 2, y - 2, x + drawWidth + 2, y + drawHeight + 2, BORDER);
            graphics.blit(texture.location(), x, y, 0.0F, 0.0F,
                    drawWidth, drawHeight, texture.width(), texture.height());
        } catch (IOException | RuntimeException exception) {
            graphics.drawCenteredString(font,
                    Component.translatable("richchat.preview.unavailable"),
                    panelX + panelW / 2, panelY + panelH / 2, RED);
        }

        drawCloseButton(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawCheckerboard(GuiGraphics graphics, int x, int y, int areaW, int areaH) {
        int cell = 12;
        for (int cy = 0; cy < areaH; cy += cell) {
            for (int cx = 0; cx < areaW; cx += cell) {
                int color = ((cx / cell + cy / cell) & 1) == 0
                        ? 0xFF343A36 : 0xFF252B28;
                graphics.fill(x + cx, y + cy,
                        Math.min(x + areaW, x + cx + cell),
                        Math.min(y + areaH, y + cy + cell), color);
            }
        }
    }

    private void drawCloseButton(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean hovered = mouseX >= closeX && mouseX < closeX + closeW
                && mouseY >= closeY && mouseY < closeY + 25;
        graphics.fill(closeX, closeY, closeX + closeW, closeY + 25,
                hovered ? 0xFF353B37 : 0xFF262B29);
        graphics.fill(closeX, closeY, closeX + 2, closeY + 25, hovered ? ACCENT : BORDER);
        graphics.fill(closeX + 2, closeY, closeX + closeW, closeY + 1, BORDER);
        graphics.fill(closeX + 2, closeY + 24, closeX + closeW, closeY + 25, BORDER);
        graphics.drawCenteredString(font, Component.translatable("gui.done"),
                closeX + closeW / 2, closeY + 8, TEXT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= closeX && mouseX < closeX + closeW
                && mouseY >= closeY && mouseY < closeY + 25) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
