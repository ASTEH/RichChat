package dev.asteh.richchat.client.gui;

import dev.asteh.richchat.client.ClientImageUploader;
import dev.asteh.richchat.client.ImageTextureCache;
import dev.asteh.richchat.client.storage.LocalImageLibrary;
import dev.asteh.richchat.client.storage.LocalImageLibrary.LocalImageEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.FileDialog;
import java.awt.Frame;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Инвентареподобная панель локальных GIF, фото и стикеров. */
public final class ImagePickerScreen extends Screen {
    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 286;
    private static final int SLOT_SIZE = 32;
    private static final int SLOT_GAP = 5;
    private static final int PAGE_SIZE = 20;

    private final Screen parent;
    private int selectedCategory;
    private int scrollOffset;
    private int panelLeft;
    private int panelTop;
    private List<LocalImageEntry> entries = new ArrayList<>();
    private String status = "";

    public ImagePickerScreen(Screen parent) {
        super(Component.translatable("richchat.picker.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - PANEL_HEIGHT) / 2;
        reloadEntries();

        int tabY = panelTop + 28;
        addRenderableWidget(Button.builder(Component.translatable("richchat.tab.gifs"),
                        button -> setCategory(1))
                .bounds(panelLeft + 8, tabY, 64, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("richchat.tab.photos"),
                        button -> setCategory(0))
                .bounds(panelLeft + 78, tabY, 64, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("richchat.tab.stickers"),
                        button -> setCategory(2))
                .bounds(panelLeft + 148, tabY, 64, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("richchat.action.add"),
                        button -> addLocalImage())
                .bounds(panelLeft + 8, panelTop + PANEL_HEIGHT - 27, 90, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),
                        button -> onClose())
                .bounds(panelLeft + PANEL_WIDTH - 88, panelTop + PANEL_HEIGHT - 27, 80, 20).build());
    }

    private void setCategory(int category) {
        selectedCategory = category;
        scrollOffset = 0;
        reloadEntries();
    }

    private void reloadEntries() {
        try {
            entries = LocalImageLibrary.entries(selectedCategory);
            if (status.startsWith("Could not")) {
                status = "";
            }
        } catch (IOException exception) {
            entries = new ArrayList<>();
            status = exception.getMessage() == null ? "Could not read local image library." : exception.getMessage();
        }
    }

    private void addLocalImage() {
        Path selected = chooseFile();
        if (selected == null) {
            return;
        }

        try {
            LocalImageEntry entry = LocalImageLibrary.importImage(selected, selectedCategory);
            selectedCategory = entry.category();
            scrollOffset = 0;
            reloadEntries();
            status = "Imported: " + entry.fileName();
        } catch (IOException | RuntimeException exception) {
            status = exception.getMessage() == null ? "Could not import image." : exception.getMessage();
        }
    }

    private Path chooseFile() {
        try {
            FileDialog dialog = new FileDialog((Frame) null, "Choose a PNG, JPEG or GIF", FileDialog.LOAD);
            dialog.setFilenameFilter((directory, name) -> {
                String lower = name.toLowerCase(java.util.Locale.ROOT);
                return lower.endsWith(".png") || lower.endsWith(".jpg")
                        || lower.endsWith(".jpeg") || lower.endsWith(".gif");
            });
            dialog.setVisible(true);
            String file = dialog.getFile();
            String directory = dialog.getDirectory();
            dialog.dispose();
            if (file == null || directory == null) {
                return null;
            }
            return Path.of(directory, file);
        } catch (java.awt.HeadlessException exception) {
            status = "Native file picker is unavailable in this environment.";
            return null;
        }
    }

    private void upload(LocalImageEntry entry) {
        Path path = LocalImageLibrary.localPath(entry);
        ClientImageUploader.upload(path, entry.category()).whenComplete((ignored, error) ->
                Minecraft.getInstance().execute(() -> {
                    if (error != null) {
                        Throwable cause = error.getCause() == null ? error : error.getCause();
                        status = cause.getMessage() == null ? "Upload could not be started." : cause.getMessage();
                    } else {
                        status = "Upload queued: " + entry.fileName();
                        try {
                            LocalImageLibrary.markUsed(entry.id());
                        } catch (IOException exception) {
                            status = "Upload queued; could not update recent list.";
                        }
                        reloadEntries();
                    }
                }));
    }

    private void delete(LocalImageEntry entry) {
        try {
            LocalImageLibrary.delete(entry.id());
            reloadEntries();
            status = "Deleted: " + entry.fileName();
        } catch (IOException exception) {
            status = exception.getMessage();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(panelLeft - 2, panelTop - 2,
                panelLeft + PANEL_WIDTH + 2, panelTop + PANEL_HEIGHT + 2, 0xFF101010);
        graphics.fill(panelLeft, panelTop,
                panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, 0xFF373737);
        graphics.fill(panelLeft + 2, panelTop + 2,
                panelLeft + PANEL_WIDTH - 2, panelTop + 3, 0xFFC6C6C6);
        graphics.fill(panelLeft + 2, panelTop + 2,
                panelLeft + 3, panelTop + PANEL_HEIGHT - 2, 0xFFC6C6C6);

        graphics.drawCenteredString(font, title, width / 2, panelTop + 8, 0xFFFFFFFF);

        int gridLeft = panelLeft + 18;
        int gridTop = panelTop + 60;
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int col = slot % 4;
            int row = slot / 4;
            int x = gridLeft + col * (SLOT_SIZE + SLOT_GAP);
            int y = gridTop + row * (SLOT_SIZE + SLOT_GAP);

            graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0xFF171717);
            graphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0xFF777777);
            graphics.fill(x + 2, y + 2, x + SLOT_SIZE - 2, y + SLOT_SIZE - 2, 0xFF424242);

            int entryIndex = scrollOffset + slot;
            if (entryIndex >= entries.size()) {
                continue;
            }

            LocalImageEntry entry = entries.get(entryIndex);
            Path path = LocalImageLibrary.localPath(entry);
            try {
                ImageTextureCache.TextureEntry texture = ImageTextureCache.get(entry.id(), path);
                double fit = Math.min(26.0D / texture.width(), 26.0D / texture.height());
                int drawWidth = Math.max(1, (int) Math.round(texture.width() * fit));
                int drawHeight = Math.max(1, (int) Math.round(texture.height() * fit));
                int drawX = x + (SLOT_SIZE - drawWidth) / 2;
                int drawY = y + (SLOT_SIZE - drawHeight) / 2;
                graphics.blit(texture.location(), drawX, drawY, 0.0F, 0.0F,
                        drawWidth, drawHeight, texture.width(), texture.height());
            } catch (IOException exception) {
                graphics.drawCenteredString(font, "?", x + SLOT_SIZE / 2, y + 12, 0xFFFF5555);
            }

            if (entry.favorite()) {
                graphics.drawString(font, "★", x + 2, y + 2, 0xFFFFFF55, false);
            }

            if (mouseX >= x && mouseX <= x + SLOT_SIZE && mouseY >= y && mouseY <= y + SLOT_SIZE) {
                graphics.renderTooltip(font, Component.literal(
                        entry.fileName() + "  (" + (entry.sizeBytes() / 1024) + " KiB)"), mouseX, mouseY);
            }
        }

        if (entries.size() > PAGE_SIZE) {
            graphics.drawCenteredString(font,
                    (scrollOffset + 1) + "–" + Math.min(scrollOffset + PAGE_SIZE, entries.size())
                            + " / " + entries.size(),
                    width / 2, panelTop + PANEL_HEIGHT - 43, 0xFFDDDDDD);
        }

        if (!status.isBlank()) {
            String visibleStatus = status.length() > 42 ? status.substring(0, 42) : status;
            graphics.drawString(font, visibleStatus, panelLeft + 8, panelTop + PANEL_HEIGHT - 43,
                    0xFFFFFF55, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int gridLeft = panelLeft + 18;
        int gridTop = panelTop + 60;

        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int col = slot % 4;
            int row = slot / 4;
            int x = gridLeft + col * (SLOT_SIZE + SLOT_GAP);
            int y = gridTop + row * (SLOT_SIZE + SLOT_GAP);
            if (mouseX < x || mouseX > x + SLOT_SIZE || mouseY < y || mouseY > y + SLOT_SIZE) {
                continue;
            }

            int index = scrollOffset + slot;
            if (index >= entries.size()) {
                return true;
            }

            LocalImageEntry entry = entries.get(index);
            if (button == 1) {
                delete(entry);
            } else {
                upload(entry);
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (mouseX >= panelLeft && mouseX <= panelLeft + PANEL_WIDTH
                && mouseY >= panelTop + 55 && mouseY <= panelTop + PANEL_HEIGHT - 35) {
            int maxOffset = Math.max(0, entries.size() - PAGE_SIZE);
            scrollOffset = Math.max(0, Math.min(maxOffset,
                    scrollOffset + (deltaY < 0 ? 4 : -4)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
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
