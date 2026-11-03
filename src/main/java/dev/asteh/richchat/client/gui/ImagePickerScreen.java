package dev.asteh.richchat.client.gui;

import org.lwjgl.glfw.GLFW;
import dev.asteh.richchat.client.ClientImageUploader;
import dev.asteh.richchat.client.ImageTextureCache;
import dev.asteh.richchat.client.storage.LocalImageLibrary;
import dev.asteh.richchat.client.storage.LocalImageLibrary.LocalImageEntry;
import dev.asteh.richchat.config.RichChatClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.FileDialog;
import java.awt.Frame;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Aftermath-inspired media library.
 *
 * This screen intentionally uses vanilla GuiGraphics instead of depending on SDM UI Lib:
 * RichChat remains standalone while sharing Aftermath's restrained dark/amber visual language.
 */
public final class ImagePickerScreen extends Screen {
    private static final int SHELL = 0xF0181B1B;
    private static final int PANEL = 0xF0222625;
    private static final int PANEL_ALT = 0xF02A2F2D;
    private static final int ROW = 0xFF262B29;
    private static final int ROW_HOVER = 0xFF353B37;
    private static final int ROW_SELECTED = 0xFF44382A;
    private static final int BORDER = 0xFF4A514B;
    private static final int ACCENT = 0xFFE0A15D;
    private static final int TEXT = 0xFFF0EEE7;
    private static final int MUTED = 0xFFB0B5AC;
    private static final int GREEN = 0xFF9DD5AF;
    private static final int RED = 0xFFE58C7F;

    private static final int BASE_WIDTH = 760;
    private static final int BASE_HEIGHT = 430;
    private static final int GAP = 8;
    private static final int PADDING = 8;

    private final Screen parent;
    private final List<LocalImageEntry> allEntries = new ArrayList<>();
    private List<LocalImageEntry> visibleEntries = List.of();

    private int selectedCategory;
    private String selectedId = "";
    private String searchText = "";
    private boolean searchFocused;
    private boolean favoritesOnly;
    private boolean uploading;
    private boolean statusIsError;
    private String status = "";

    private int shellX;
    private int shellY;
    private int shellW;
    private int shellH;
    private int headerY;
    private int bodyY;
    private int bodyH;
    private int footerY;
    private boolean compactLayout;

    private Rect libraryPanel;
    private Rect sidebarPanel;
    private Rect detailsPanel;
    private Rect searchRect;
    private Rect gridRect;
    private Rect importButton;
    private Rect favoriteButton;
    private Rect deleteButton;
    private Rect sendButton;
    private Rect closeButton;
    private Rect[] categoryButtons = new Rect[3];

    private int slotSize;
    private int slotGap;
    private int gridColumns;
    private int gridRows;
    private int scrollOffset;

    public ImagePickerScreen(Screen parent) {
        super(Component.translatable("richchat.picker.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        calculateLayout();
        reloadEntries();
    }

    private void calculateLayout() {
        double configuredScale = RichChatClientConfig.PANEL_SCALE.get();
        double scale = Math.max(0.5D, Math.min(2.0D, configuredScale));
        shellW = Math.min(Math.max(1, width - 16), (int) Math.round(BASE_WIDTH * scale));
        shellH = Math.min(Math.max(1, height - 16), (int) Math.round(BASE_HEIGHT * scale));
        shellX = (width - shellW) / 2;
        shellY = (height - shellH) / 2;

        compactLayout = shellW < 650 || shellH < 365;
        headerY = shellY + 8;
        bodyY = shellY + 52;
        footerY = shellY + shellH - 53;
        bodyH = Math.max(1, footerY - bodyY - 7);

        int innerX = shellX + PADDING;
        int innerW = Math.max(1, shellW - PADDING * 2);
        if (compactLayout) {
            sidebarPanel = null;
            detailsPanel = null;
            libraryPanel = new Rect(innerX, bodyY, innerW, bodyH);
            searchRect = new Rect(libraryPanel.x + libraryPanel.width - 156,
                    libraryPanel.y + 8, 146, 20);
            int tabsY = libraryPanel.y + 8;
            int availableTabsW = Math.max(150, libraryPanel.width - 174);
            int tabW = Math.max(46, (availableTabsW - 12) / 3);
            for (int i = 0; i < 3; i++) {
                categoryButtons[i] = new Rect(libraryPanel.x + 8 + i * (tabW + 6), tabsY, tabW, 20);
            }
            gridRect = new Rect(libraryPanel.x + 9, libraryPanel.y + 37,
                    Math.max(1, libraryPanel.width - 18), Math.max(1, libraryPanel.height - 45));
        } else {
            int sideW = 124;
            int detailsW = 166;
            int mainW = Math.max(1, innerW - sideW - detailsW - GAP * 2);
            sidebarPanel = new Rect(innerX, bodyY, sideW, bodyH);
            libraryPanel = new Rect(innerX + sideW + GAP, bodyY, mainW, bodyH);
            detailsPanel = new Rect(libraryPanel.x + mainW + GAP, bodyY, detailsW, bodyH);
            searchRect = new Rect(libraryPanel.x + libraryPanel.width - 150,
                    libraryPanel.y + 8, 140, 20);
            for (int i = 0; i < 3; i++) {
                categoryButtons[i] = new Rect(sidebarPanel.x + 7,
                        sidebarPanel.y + 31 + i * 30, sidebarPanel.width - 14, 25);
            }
            gridRect = new Rect(libraryPanel.x + 9, libraryPanel.y + 37,
                    Math.max(1, libraryPanel.width - 18), Math.max(1, libraryPanel.height - 45));
        }

        slotSize = compactLayout ? 36 : 42;
        slotGap = compactLayout ? 5 : 7;
        gridColumns = Math.max(2, Math.min(9,
                (gridRect.width + slotGap) / (slotSize + slotGap)));
        gridRows = Math.max(1, (gridRect.height + slotGap) / (slotSize + slotGap));

        int buttonY = footerY + 11;
        int buttonGap = 5;
        if (compactLayout) {
            int x = shellX + shellW - PADDING;
            closeButton = placeRight(x, buttonY, 58, 25);
            x = closeButton.x - buttonGap;
            sendButton = placeRight(x, buttonY, 76, 25);
            x = sendButton.x - buttonGap;
            deleteButton = placeRight(x, buttonY, 66, 25);
            favoriteButton = placeRight(deleteButton.x - buttonGap, buttonY, 30, 25);
            importButton = placeRight(favoriteButton.x - buttonGap, buttonY, 72, 25);
        } else {
            int x = shellX + shellW - PADDING;
            closeButton = placeRight(x, buttonY, 66, 27);
            x = closeButton.x - buttonGap;
            sendButton = placeRight(x, buttonY, 88, 27);
            x = sendButton.x - buttonGap;
            deleteButton = placeRight(x, buttonY, 76, 27);
            x = deleteButton.x - buttonGap;
            favoriteButton = placeRight(x, buttonY, 95, 27);
            importButton = placeRight(favoriteButton.x - buttonGap, buttonY, 92, 27);
        }
    }

    private static Rect placeRight(int right, int y, int width, int height) {
        return new Rect(right - width, y, width, height);
    }

    private void reloadEntries() {
        String previousSelection = selectedId;
        allEntries.clear();
        try {
            for (int category = 0; category < 3; category++) {
                allEntries.addAll(LocalImageLibrary.entries(category));
            }
            statusIsError = false;
            if (status.startsWith("Не удалось") || status.startsWith("Could not")) {
                status = "";
            }
        } catch (IOException exception) {
            status = message(exception, "Could not read the local media library.");
            statusIsError = true;
        }
        applyFilter(previousSelection);
    }

    private void applyFilter(String preferredSelection) {
        String query = searchText.toLowerCase(Locale.ROOT).trim();
        visibleEntries = allEntries.stream()
                .filter(entry -> entry.category() == selectedCategory)
                .filter(entry -> !favoritesOnly || entry.favorite())
                .filter(entry -> query.isEmpty()
                        || entry.fileName().toLowerCase(Locale.ROOT).contains(query))
                .toList();

        boolean selectionStillVisible = visibleEntries.stream()
                .anyMatch(entry -> entry.id().equals(preferredSelection));
        selectedId = selectionStillVisible ? preferredSelection
                : (visibleEntries.isEmpty() ? "" : visibleEntries.get(0).id());
        scrollOffset = Math.min(scrollOffset, Math.max(0, visibleEntries.size() - pageSize()));
    }

    private int pageSize() {
        return Math.max(1, gridColumns * gridRows);
    }

    private LocalImageEntry selectedEntry() {
        return visibleEntries.stream().filter(entry -> entry.id().equals(selectedId))
                .findFirst().orElse(null);
    }

    private void setCategory(int category) {
        if (category < 0 || category > 2) return;
        selectedCategory = category;
        favoritesOnly = false;
        scrollOffset = 0;
        applyFilter("");
    }

    private void addLocalImage() {
        Path selected = chooseFile();
        if (selected == null) return;

        try {
            LocalImageEntry imported = LocalImageLibrary.importImage(selected, selectedCategory);
            selectedCategory = imported.category();
            favoritesOnly = false;
            searchText = "";
            scrollOffset = 0;
            reloadEntries();
            selectedId = imported.id();
            status = tr("richchat.status.imported", imported.fileName());
            statusIsError = false;
        } catch (IOException | RuntimeException exception) {
            status = message(exception, "Could not import image.");
            statusIsError = true;
        }
    }

    private Path chooseFile() {
        try {
            FileDialog dialog = new FileDialog((Frame) null,
                    tr("richchat.dialog.choose_file"), FileDialog.LOAD);
            dialog.setFilenameFilter((directory, name) -> {
                String lower = name.toLowerCase(Locale.ROOT);
                return lower.endsWith(".png") || lower.endsWith(".jpg")
                        || lower.endsWith(".jpeg") || lower.endsWith(".gif");
            });
            dialog.setVisible(true);
            String file = dialog.getFile();
            String directory = dialog.getDirectory();
            dialog.dispose();
            return file == null || directory == null ? null : Path.of(directory, file);
        } catch (java.awt.HeadlessException exception) {
            status = tr("richchat.status.file_dialog_unavailable");
            statusIsError = true;
            return null;
        }
    }

    private void uploadSelected() {
        LocalImageEntry entry = selectedEntry();
        if (entry == null || uploading) return;
        if (Minecraft.getInstance().getConnection() == null) {
            status = tr("richchat.status.connect_to_upload");
            statusIsError = true;
            return;
        }

        uploading = true;
        status = tr("richchat.status.uploading", entry.fileName());
        statusIsError = false;
        ClientImageUploader.upload(LocalImageLibrary.localPath(entry), entry.category())
                .whenComplete((ignored, error) -> Minecraft.getInstance().execute(() -> {
                    uploading = false;
                    if (error != null) {
                        Throwable cause = error.getCause() == null ? error : error.getCause();
                        status = message(cause, "Could not start the upload.");
                        statusIsError = true;
                        return;
                    }

                    try {
                        LocalImageLibrary.markUsed(entry.id());
                    } catch (IOException exception) {
                        // The upload was queued; failure to update local recency must not hide that.
                    }
                    status = tr("richchat.status.upload_queued", entry.fileName());
                    statusIsError = false;
                    reloadEntries();
                }));
    }

    private void toggleFavorite() {
        LocalImageEntry entry = selectedEntry();
        if (entry == null) return;
        try {
            LocalImageLibrary.setFavorite(entry.id(), !entry.favorite());
            status = tr(entry.favorite() ? "richchat.status.favorite_removed"
                    : "richchat.status.favorite_added");
            statusIsError = false;
            reloadEntries();
        } catch (IOException exception) {
            status = message(exception, "Could not update favorites.");
            statusIsError = true;
        }
    }

    private void deleteSelected() {
        LocalImageEntry entry = selectedEntry();
        if (entry == null) return;
        try {
            LocalImageLibrary.delete(entry.id());
            selectedId = "";
            status = tr("richchat.status.deleted", entry.fileName());
            statusIsError = false;
            reloadEntries();
        } catch (IOException exception) {
            status = message(exception, "Could not delete image.");
            statusIsError = true;
        }
    }

    private static String message(Throwable throwable, String fallback) {
        String value = throwable.getMessage();
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(shellX, shellY, shellX + shellW, shellY + shellH, SHELL);
        graphics.fill(shellX, shellY, shellX + shellW, shellY + 2, ACCENT);
        graphics.fill(shellX, shellY + shellH - 1, shellX + shellW, shellY + shellH, BORDER);

        graphics.drawString(font, tr("richchat.ui.kicker"), shellX + PADDING, headerY, ACCENT);
        graphics.drawString(font, title, shellX + PADDING, headerY + 13, TEXT);
        String summary = tr("richchat.ui.item_count", visibleEntries.size(), allEntries.size());
        drawRightAligned(graphics, summary, shellX + shellW - PADDING, headerY + 16, MUTED);

        if (sidebarPanel != null) drawSidebar(graphics, mouseX, mouseY);
        drawLibrary(graphics, mouseX, mouseY);
        if (detailsPanel != null) drawDetails(graphics, mouseX, mouseY);
        drawFooter(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawSidebar(GuiGraphics graphics, int mouseX, int mouseY) {
        drawPanel(graphics, sidebarPanel);
        graphics.drawString(font, tr("richchat.ui.library"), sidebarPanel.x + 8,
                sidebarPanel.y + 9, ACCENT);
        for (int i = 0; i < 3; i++) {
            int category = i;
            int count = (int) allEntries.stream().filter(entry -> entry.category() == category).count();
            String label = tr(categoryKey(category)) + "  " + count;
            drawButton(graphics, categoryButtons[category], label, mouseX, mouseY,
                    selectedCategory == category, false);
        }

        Rect favoritesRect = new Rect(sidebarPanel.x + 7, sidebarPanel.y + 132,
                sidebarPanel.width - 14, 25);
        drawButton(graphics, favoritesRect, tr("richchat.ui.favorites"), mouseX, mouseY,
                favoritesOnly, false);
        graphics.drawString(font, tr("richchat.ui.library_hint"), sidebarPanel.x + 8,
                sidebarPanel.y + sidebarPanel.height - 31, MUTED);
    }

    private String categoryKey(int category) {
        return switch (category) {
            case 1 -> "richchat.tab.gifs";
            case 2 -> "richchat.tab.stickers";
            default -> "richchat.tab.photos";
        };
    }

    private void drawLibrary(GuiGraphics graphics, int mouseX, int mouseY) {
        drawPanel(graphics, libraryPanel);
        if (!compactLayout) {
            graphics.drawString(font, tr(categoryKey(selectedCategory)),
                    libraryPanel.x + 9, libraryPanel.y + 10, ACCENT);
        }

        if (compactLayout) {
            for (int i = 0; i < 3; i++) {
                drawButton(graphics, categoryButtons[i], tr(categoryKey(i)),
                        mouseX, mouseY, selectedCategory == i, false);
            }
        }

        drawSearch(graphics, mouseX, mouseY);
        if (visibleEntries.isEmpty()) {
            String empty = searchText.isBlank()
                    ? tr("richchat.ui.empty_library")
                    : tr("richchat.ui.no_search_results");
            graphics.drawCenteredString(font, empty, gridRect.x + gridRect.width / 2,
                    gridRect.y + Math.max(8, gridRect.height / 2 - 4), MUTED);
            return;
        }

        int visibleSlots = pageSize();
        for (int slot = 0; slot < visibleSlots; slot++) {
            int column = slot % gridColumns;
            int row = slot / gridColumns;
            int x = gridRect.x + column * (slotSize + slotGap);
            int y = gridRect.y + row * (slotSize + slotGap);
            int index = scrollOffset + slot;
            if (index >= visibleEntries.size()) break;

            LocalImageEntry entry = visibleEntries.get(index);
            boolean selected = entry.id().equals(selectedId);
            boolean hovered = contains(x, y, slotSize, slotSize, mouseX, mouseY);
            int background = selected ? ROW_SELECTED : hovered ? ROW_HOVER : ROW;
            graphics.fill(x, y, x + slotSize, y + slotSize, background);
            graphics.fill(x, y, x + slotSize, y + 1, selected ? ACCENT : BORDER);
            graphics.fill(x, y + slotSize - 1, x + slotSize, y + slotSize, BORDER);
            graphics.fill(x, y, x + 2, y + slotSize, selected ? ACCENT : BORDER);
            drawCheckerboard(graphics, x + 4, y + 4, slotSize - 8, slotSize - 8);
            drawEntryTexture(graphics, entry, x + 5, y + 5, slotSize - 10, slotSize - 10);

            if (entry.favorite()) {
                graphics.drawString(font, "★", x + slotSize - 9, y + 1, ACCENT, true);
            }
            if (entry.category() == 1) {
                graphics.fill(x + 3, y + slotSize - 10, x + 19, y + slotSize - 3, 0xD0181B1B);
                graphics.drawString(font, "GIF", x + 5, y + slotSize - 10, TEXT, false);
            }

            if (hovered) {
                graphics.renderTooltip(font, Component.literal(entry.fileName()
                        + "  •  " + (entry.sizeBytes() / 1024) + " KiB"), mouseX, mouseY);
            }
        }

        if (visibleEntries.size() > pageSize()) {
            String range = (scrollOffset + 1) + "–"
                    + Math.min(scrollOffset + pageSize(), visibleEntries.size())
                    + " / " + visibleEntries.size();
            drawRightAligned(graphics, range, gridRect.x + gridRect.width,
                    gridRect.y + gridRect.height - 8, MUTED);
        }
    }

    private void drawSearch(GuiGraphics graphics, int mouseX, int mouseY) {
        int color = searchFocused ? ACCENT : BORDER;
        graphics.fill(searchRect.x, searchRect.y, searchRect.x + searchRect.width,
                searchRect.y + searchRect.height, PANEL_ALT);
        drawRectBorder(graphics, searchRect, color);
        String value = searchText.isEmpty() && !searchFocused
                ? tr("richchat.ui.search_placeholder") : searchText;
        int textColor = searchText.isEmpty() && !searchFocused ? MUTED : TEXT;
        drawTruncated(graphics, (searchFocused ? "› " : "⌕ ") + value,
                searchRect.x + 5, searchRect.y + 6, searchRect.width - 10, textColor);
    }

    private void drawDetails(GuiGraphics graphics, int mouseX, int mouseY) {
        drawPanel(graphics, detailsPanel);
        graphics.drawString(font, tr("richchat.ui.preview"), detailsPanel.x + 8,
                detailsPanel.y + 9, ACCENT);
        LocalImageEntry entry = selectedEntry();
        if (entry == null) {
            graphics.drawCenteredString(font, tr("richchat.ui.no_selection"),
                    detailsPanel.x + detailsPanel.width / 2, detailsPanel.y + 62, MUTED);
            return;
        }

        int previewSize = Math.min(116, detailsPanel.width - 20);
        int previewX = detailsPanel.x + (detailsPanel.width - previewSize) / 2;
        int previewY = detailsPanel.y + 31;
        drawCheckerboard(graphics, previewX, previewY, previewSize, previewSize);
        drawEntryTexture(graphics, entry, previewX + 2, previewY + 2,
                previewSize - 4, previewSize - 4);

        int textY = previewY + previewSize + 12;
        drawWrappedName(graphics, entry.fileName(), detailsPanel.x + 8, textY,
                detailsPanel.width - 16, 2);
        textY += 30;
        graphics.drawString(font, tr("richchat.ui.format",
                entry.extension().toUpperCase(Locale.ROOT)), detailsPanel.x + 8, textY, MUTED);
        graphics.drawString(font, tr("richchat.ui.file_size",
                String.format(Locale.ROOT, "%.1f", entry.sizeBytes() / 1024.0D)),
                detailsPanel.x + 8, textY + 13, MUTED);
        graphics.drawString(font, entry.favorite() ? tr("richchat.ui.favorite")
                : tr("richchat.ui.not_favorite"), detailsPanel.x + 8, textY + 26,
                entry.favorite() ? ACCENT : MUTED);
        if (uploading) {
            graphics.drawString(font, tr("richchat.ui.uploading"), detailsPanel.x + 8,
                    detailsPanel.y + detailsPanel.height - 20, GREEN);
        }
    }

    private void drawEntryTexture(GuiGraphics graphics, LocalImageEntry entry,
                                  int x, int y, int maxWidth, int maxHeight) {
        try {
            ImageTextureCache.TextureEntry texture = ImageTextureCache.get(
                    "library_" + entry.id(), LocalImageLibrary.localPath(entry));
            double fit = Math.min((double) maxWidth / texture.width(),
                    (double) maxHeight / texture.height());
            int drawWidth = Math.max(1, (int) Math.round(texture.width() * fit));
            int drawHeight = Math.max(1, (int) Math.round(texture.height() * fit));
            int drawX = x + (maxWidth - drawWidth) / 2;
            int drawY = y + (maxHeight - drawHeight) / 2;
            graphics.blit(texture.location(), drawX, drawY, 0.0F, 0.0F,
                    drawWidth, drawHeight, texture.width(), texture.height());
        } catch (IOException | RuntimeException exception) {
            graphics.drawCenteredString(font, "?", x + maxWidth / 2, y + maxHeight / 2 - 4, RED);
        }
    }

    private void drawCheckerboard(GuiGraphics graphics, int x, int y, int width, int height) {
        int cell = 8;
        for (int cy = 0; cy < height; cy += cell) {
            for (int cx = 0; cx < width; cx += cell) {
                boolean alternate = ((cx / cell) + (cy / cell)) % 2 == 0;
                int color = alternate ? 0xFF343A36 : 0xFF252B28;
                graphics.fill(x + cx, y + cy,
                        Math.min(x + width, x + cx + cell),
                        Math.min(y + height, y + cy + cell), color);
            }
        }
    }

    private void drawFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(shellX + PADDING, footerY, shellX + shellW - PADDING,
                footerY + 1, BORDER);
        LocalImageEntry entry = selectedEntry();
        String selectedText = entry == null ? tr("richchat.ui.nothing_selected")
                : tr("richchat.ui.selected", entry.fileName());
        String shownStatus = status.isBlank() ? tr("richchat.ui.footer_hint") : status;
        int statusColor = statusIsError ? RED : status.isBlank() ? MUTED : GREEN;
        int labelWidth = Math.max(40, importButton.x - shellX - PADDING - 12);
        drawTruncated(graphics, selectedText, shellX + PADDING, footerY + 8,
                labelWidth, TEXT);
        drawTruncated(graphics, shownStatus, shellX + PADDING, footerY + 23,
                labelWidth, statusColor);

        drawButton(graphics, importButton, tr("richchat.action.import"),
                mouseX, mouseY, false, true);
        drawButton(graphics, favoriteButton, compactLayout
                        ? (entry != null && entry.favorite() ? "★" : "☆")
                        : tr(entry != null && entry.favorite()
                            ? "richchat.action.unfavorite" : "richchat.action.favorite"),
                mouseX, mouseY, false, false, entry != null);
        drawButton(graphics, deleteButton, tr("richchat.action.delete"),
                mouseX, mouseY, false, false, entry != null);
        drawButton(graphics, sendButton, uploading
                        ? tr("richchat.action.sending") : tr("richchat.action.send"),
                mouseX, mouseY, false, true, entry != null && !uploading);
        drawButton(graphics, closeButton, tr("gui.done"),
                mouseX, mouseY, false, false);
    }

    private void drawButton(GuiGraphics graphics, Rect rect, String label,
                            int mouseX, int mouseY, boolean selected, boolean primary) {
        drawButton(graphics, rect, label, mouseX, mouseY, selected, primary, true);
    }

    private void drawButton(GuiGraphics graphics, Rect rect, String label,
                            int mouseX, int mouseY, boolean selected,
                            boolean primary, boolean enabled) {
        boolean hovered = contains(rect, mouseX, mouseY);
        int background;
        int accent;
        if (!enabled) {
            background = 0xFF202422;
            accent = BORDER;
        } else if (primary) {
            background = hovered ? 0xFFE6B16F : ACCENT;
            accent = 0xFFFFD7A2;
        } else {
            background = selected ? ROW_SELECTED : hovered ? ROW_HOVER : ROW;
            accent = selected ? ACCENT : hovered ? 0xFF889185 : BORDER;
        }
        graphics.fill(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, background);
        graphics.fill(rect.x, rect.y, rect.x + 2, rect.y + rect.height, accent);
        graphics.fill(rect.x + 2, rect.y, rect.x + rect.width, rect.y + 1, BORDER);
        graphics.fill(rect.x + 2, rect.y + rect.height - 1,
                rect.x + rect.width, rect.y + rect.height, BORDER);
        int color = primary && enabled ? 0xFF171A18 : enabled ? TEXT : MUTED;
        graphics.drawCenteredString(font, fit(label, rect.width - 10),
                rect.x + rect.width / 2, rect.y + (rect.height - font.lineHeight) / 2, color);
    }

    private void drawPanel(GuiGraphics graphics, Rect rect) {
        graphics.fill(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, PANEL);
        graphics.fill(rect.x, rect.y, rect.x + rect.width, rect.y + 1, BORDER);
        graphics.fill(rect.x, rect.y + rect.height - 1,
                rect.x + rect.width, rect.y + rect.height, BORDER);
        graphics.fill(rect.x, rect.y, rect.x + 1, rect.y + rect.height, BORDER);
        graphics.fill(rect.x + rect.width - 1, rect.y,
                rect.x + rect.width, rect.y + rect.height, BORDER);
        graphics.fill(rect.x + 1, rect.y + 1,
                rect.x + rect.width - 1, rect.y + 3, 0xFF353B36);
    }

    private void drawRectBorder(GuiGraphics graphics, Rect rect, int color) {
        graphics.fill(rect.x, rect.y, rect.x + rect.width, rect.y + 1, color);
        graphics.fill(rect.x, rect.y + rect.height - 1,
                rect.x + rect.width, rect.y + rect.height, color);
        graphics.fill(rect.x, rect.y, rect.x + 1, rect.y + rect.height, color);
        graphics.fill(rect.x + rect.width - 1, rect.y,
                rect.x + rect.width, rect.y + rect.height, color);
    }

    private void drawWrappedName(GuiGraphics graphics, String value, int x, int y,
                                 int maxWidth, int maxLines) {
        String remaining = value;
        for (int line = 0; line < maxLines && !remaining.isEmpty(); line++) {
            String shown = remaining;
            while (!shown.isEmpty() && font.width(shown) > maxWidth) {
                int lastSpace = shown.lastIndexOf(' ');
                if (lastSpace > 0) shown = shown.substring(0, lastSpace);
                else shown = shown.substring(0, shown.length() - 1);
            }
            graphics.drawString(font, shown, x, y + line * 10, TEXT);
            remaining = remaining.substring(Math.min(remaining.length(), shown.length())).stripLeading();
        }
    }

    private void drawRightAligned(GuiGraphics graphics, String value, int rightX, int y, int color) {
        String text = fit(value, Math.max(8, rightX - shellX - PADDING - 210));
        graphics.drawString(font, text, rightX - font.width(text), y, color);
    }

    private void drawTruncated(GuiGraphics graphics, String value, int x, int y,
                               int maxWidth, int color) {
        graphics.drawString(font, fit(value, maxWidth), x, y, color);
    }

    private String fit(String value, int maxWidth) {
        String text = value == null ? "" : value;
        int allowed = Math.max(0, maxWidth);
        while (!text.isEmpty() && font.width(text) > allowed) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private static boolean contains(Rect rect, double x, double y) {
        return rect != null && contains(rect.x, rect.y, rect.width, rect.height, x, y);
    }

    private static boolean contains(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (contains(searchRect, mouseX, mouseY)) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;

        if (compactLayout) {
            for (int i = 0; i < 3; i++) {
                if (contains(categoryButtons[i], mouseX, mouseY)) {
                    setCategory(i);
                    return true;
                }
            }
        } else {
            for (int i = 0; i < 3; i++) {
                if (contains(categoryButtons[i], mouseX, mouseY)) {
                    setCategory(i);
                    return true;
                }
            }
            Rect favoritesRect = new Rect(sidebarPanel.x + 7, sidebarPanel.y + 132,
                    sidebarPanel.width - 14, 25);
            if (contains(favoritesRect, mouseX, mouseY)) {
                favoritesOnly = !favoritesOnly;
                scrollOffset = 0;
                applyFilter(selectedId);
                return true;
            }
        }

        if (contains(gridRect, mouseX, mouseY)) {
            int column = (int) ((mouseX - gridRect.x) / (slotSize + slotGap));
            int row = (int) ((mouseY - gridRect.y) / (slotSize + slotGap));
            if (column >= 0 && column < gridColumns && row >= 0 && row < gridRows) {
                int cellX = gridRect.x + column * (slotSize + slotGap);
                int cellY = gridRect.y + row * (slotSize + slotGap);
                if (mouseX < cellX + slotSize && mouseY < cellY + slotSize) {
                    int index = scrollOffset + row * gridColumns + column;
                    if (index < visibleEntries.size()) {
                        selectedId = visibleEntries.get(index).id();
                    }
                    return true;
                }
            }
        }

        if (contains(importButton, mouseX, mouseY)) {
            addLocalImage();
            return true;
        }
        if (contains(favoriteButton, mouseX, mouseY)) {
            toggleFavorite();
            return true;
        }
        if (contains(deleteButton, mouseX, mouseY)) {
            deleteSelected();
            return true;
        }
        if (contains(sendButton, mouseX, mouseY)) {
            uploadSelected();
            return true;
        }
        if (contains(closeButton, mouseX, mouseY)) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double deltaX, double deltaY) {
        if (contains(gridRect, mouseX, mouseY) && deltaY != 0.0D) {
            int step = Math.max(1, gridColumns * 2);
            int maxOffset = Math.max(0, visibleEntries.size() - pageSize());
            scrollOffset = Math.max(0, Math.min(maxOffset,
                    scrollOffset + (deltaY < 0 ? step : -step)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchFocused && !Character.isISOControl(codePoint) && searchText.length() < 48) {
            searchText += codePoint;
            scrollOffset = 0;
            applyFilter(selectedId);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchText.isEmpty()) {
                    searchText = searchText.substring(0, searchText.length() - 1);
                    scrollOffset = 0;
                    applyFilter(selectedId);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                searchFocused = false;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Rect(int x, int y, int width, int height) {}
}
