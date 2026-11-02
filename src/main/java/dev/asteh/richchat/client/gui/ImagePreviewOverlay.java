package dev.asteh.richchat.client.gui;

import dev.asteh.richchat.client.GifAnimationCache;
import dev.asteh.richchat.client.ImageTextureCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;

/** Полноразмерный просмотр изображения поверх игры. */
public final class ImagePreviewOverlay extends Screen {
    private final Screen parent;
    private final Path imagePath;
    private final String imageId;

    public ImagePreviewOverlay(Screen parent, Path imagePath, String imageId) {
        super(Component.translatable("richchat.preview.title"));
        this.parent = parent;
        this.imagePath = imagePath;
        this.imageId = imageId;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(this.width / 2 - 50, this.height - 32, 100, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0xD0101010);
        graphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFFFF);

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

            int maxWidth = Math.max(32, width - 48);
            int maxHeight = Math.max(32, height - 82);
            double fit = Math.min((double) maxWidth / texture.width(),
                    (double) maxHeight / texture.height());
            int drawWidth = Math.max(1, (int) Math.round(texture.width() * fit));
            int drawHeight = Math.max(1, (int) Math.round(texture.height() * fit));
            int x = (width - drawWidth) / 2;
            int y = Math.max(42, (height - drawHeight) / 2);
            graphics.fill(x - 2, y - 2, x + drawWidth + 2, y + drawHeight + 2, 0xFF303030);
            graphics.blit(texture.location(), x, y, 0.0F, 0.0F,
                    drawWidth, drawHeight, texture.width(), texture.height());
        } catch (IOException | RuntimeException exception) {
            graphics.drawCenteredString(font,
                    Component.translatable("richchat.preview.unavailable"),
                    width / 2, height / 2, 0xFFFF5555);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
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
