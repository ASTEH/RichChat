package dev.asteh.richchat.client.gui;

import org.lwjgl.glfw.GLFW;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * A small in-game file browser. It avoids java.awt so importing media also works
 * when the game is launched in an environment where AWT is headless.
 */
final class ImageFileBrowserScreen extends Screen {
    private static final int SHELL = 0xF0181B1B;
    private static final int PANEL = 0xF0222625;
    private static final int ROW = 0xFF262B29;
    private static final int ROW_HOVER = 0xFF353B37;
    private static final int ROW_SELECTED = 0xFF44382A;
    private static final int BORDER = 0xFF4A514B;
    private static final int ACCENT = 0xFFE0A15D;
    private static final int TEXT = 0xFFF0EEE7;
    private static final int MUTED = 0xFFB0B5AC;
    private static final int GREEN = 0xFF9DD5AF;
    private static final int RED = 0xFFE58C7F;

    private static final int MAX_DIRECTORY_ENTRIES = 500;
    private static final int ROW_HEIGHT = 22;

    private final Screen parent;
    private final Consumer<Path> onSelected;
    private final List<FileEntry> entries = new ArrayList<>();

    private Path currentDirectory;
    private Path selectedFile;
    private EditBox pathInput;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int pathY;
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private int footerY;
    private int scrollOffset;

    private Rect upButton;
    private Rect goButton;
    private Rect chooseButton;
    private Rect cancelButton;

    private String status = "";
    private boolean statusIsError;
    private boolean truncated;

    ImageFileBrowserScreen(Screen parent, Consumer<Path> onSelected) {
        super(Component.translatable("richchat.file_browser.title"));
        this.parent = parent;
        this.onSelected = onSelected;
        this.currentDirectory = initialDirectory();
    }

    private static Path initialDirectory() {
        try {
            String home = System.getProperty("user.home");
            if (home != null && !home.isBlank()) {
                Path homePath = Path.of(home).toAbsolutePath().normalize();
                if (Files.isDirectory(homePath)) return homePath;
            }
        } catch (SecurityException | InvalidPathException ignored) {
            // Fall back to the game directory.
        }
        return Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize();
    }

    @Override
    protected void init() {
        panelW = Math.max(1, Math.min(width - 16, 760));
        panelH = Math.max(1, Math.min(height - 16, 470));
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        pathY = panelY + 51;
        footerY = panelY + panelH - 43;

        upButton = new Rect(panelX + 10, pathY, 28, 20);
        goButton = new Rect(panelX + panelW - 78, pathY, 68, 20);
        int inputX = upButton.x + upButton.width + 6;
        int inputWidth = Math.max(24, goButton.x - inputX - 6);

        pathInput = new EditBox(font, inputX, pathY, inputWidth, 20,
                Component.translatable("richchat.file_browser.path"));
        pathInput.setMaxLength(1024);
        pathInput.setValue(currentDirectory.toString());
        pathInput.setHint(Component.translatable("richchat.file_browser.path_hint"));
        addRenderableWidget(pathInput);

        listX = panelX + 10;
        listY = pathY + 29;
        listW = panelW - 20;
        listH = Math.max(1, footerY - listY - 8);

        chooseButton = new Rect(panelX + panelW - 184, footerY + 10, 88, 25);
        cancelButton = new Rect(panelX + panelW - 88, footerY + 10, 78, 25);

        refreshDirectory(currentDirectory, false);
    }

    private void refreshDirectory(Path directory, boolean report) {
        Path normalized = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            setStatus(tr("richchat.file_browser.not_directory"), true);
            return;
        }

        List<FileEntry> found = new ArrayList<>();
        truncated = false;
        try (Stream<Path> stream = Files.list(normalized)) {
            List<Path> children = stream.limit(MAX_DIRECTORY_ENTRIES + 1L).toList();
            truncated = children.size() > MAX_DIRECTORY_ENTRIES;

            for (int i = 0; i < Math.min(children.size(), MAX_DIRECTORY_ENTRIES); i++) {
                Path child = children.get(i);
                try {
                    if (Files.isDirectory(child)) {
                        found.add(new FileEntry(child, true));
                    } else if (Files.isRegularFile(child) && isSupportedImage(child)) {
                        found.add(new FileEntry(child, false));
                    }
                } catch (SecurityException ignored) {
                    // Skip entries that the current user cannot inspect.
                }
            }
        } catch (IOException | SecurityException exception) {
            setStatus(message(exception, tr("richchat.file_browser.access_denied")), true);
            return;
        }

        found.sort(Comparator
                .comparing(FileEntry::directory).reversed()
                .thenComparing(entry -> entry.path().getFileName().toString(),
                        String.CASE_INSENSITIVE_ORDER));

        currentDirectory = normalized;
        entries.clear();
        entries.addAll(found);
        selectedFile = null;
        scrollOffset = 0;
        pathInput.setValue(currentDirectory.toString());
        pathInput.setFocused(false);

        if (truncated) {
            setStatus(tr("richchat.file_browser.list_truncated", MAX_DIRECTORY_ENTRIES), false);
        } else if (report) {
            setStatus(tr("richchat.file_browser.folder_opened", entries.size()), false);
        } else {
            setStatus(tr("richchat.file_browser.instructions"), false);
        }
    }

    private static boolean isSupportedImage(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg")
                || name.endsWith(".jpeg") || name.endsWith(".gif");
    }

    private void navigateFromInput() {
        try {
            String value = pathInput.getValue().trim();
            if (value.isEmpty()) {
                setStatus(tr("richchat.file_browser.enter_path"), true);
                return;
            }
            Path path = Path.of(value).toAbsolutePath().normalize();
            if (Files.isRegularFile(path) && isSupportedImage(path)) {
                Path directory = path.getParent();
                if (directory == null) {
                    setStatus(tr("richchat.file_browser.invalid_path"), true);
                    return;
                }
                refreshDirectory(directory, true);
                selectedFile = path;
                int selectedIndex = -1;
                for (int i = 0; i < entries.size(); i++) {
                    if (entries.get(i).path().equals(path)) {
                        selectedIndex = i;
                        break;
                    }
                }
                if (selectedIndex >= 0) {
                    int visibleRows = Math.max(1, listH / ROW_HEIGHT);
                    if (selectedIndex < scrollOffset) {
                        scrollOffset = selectedIndex;
                    } else if (selectedIndex >= scrollOffset + visibleRows) {
                        scrollOffset = selectedIndex - visibleRows + 1;
                    }
                }
                setStatus(tr("richchat.file_browser.ready"), false);
                return;
            }
            refreshDirectory(path, true);
        } catch (InvalidPathException | SecurityException exception) {
            setStatus(message(exception, tr("richchat.file_browser.invalid_path")), true);
        }
    }

    private void navigateUp() {
        Path parentPath = currentDirectory.getParent();
        if (parentPath != null) refreshDirectory(parentPath, true);
    }

    private void chooseFile() {
        if (selectedFile == null) return;
        onSelected.accept(selectedFile);
        Minecraft.getInstance().setScreen(parent);
    }

    private void setStatus(String value, boolean error) {
        status = value == null ? "" : value;
        statusIsError = error;
    }

    private String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static String message(Throwable throwable, String fallback) {
        String value = throwable.getMessage();
        return value == null || value.isBlank() ? fallback : value;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, SHELL);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, ACCENT);
        graphics.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, BORDER);

        graphics.drawString(font, tr("richchat.ui.kicker"), panelX + 10, panelY + 10, ACCENT);
        graphics.drawString(font, title, panelX + 10, panelY + 23, TEXT);
        graphics.drawString(font, tr("richchat.file_browser.subtitle"),
                panelX + 10, panelY + 37, MUTED);

        drawButton(graphics, upButton, "↑", mouseX, mouseY, false);
        drawButton(graphics, goButton, tr("richchat.file_browser.go"), mouseX, mouseY, false);

        graphics.fill(listX, listY, listX + listW, listY + listH, PANEL);
        graphics.fill(listX, listY, listX + listW, listY + 1, BORDER);

        int visibleRows = Math.max(1, listH / ROW_HEIGHT);
        if (entries.isEmpty()) {
            graphics.drawCenteredString(font, tr("richchat.file_browser.empty"),
                    listX + listW / 2, listY + Math.max(4, listH / 2 - 4), MUTED);
        }

        for (int row = 0; row < visibleRows; row++) {
            int index = scrollOffset + row;
            if (index >= entries.size()) break;

            FileEntry entry = entries.get(index);
            int y = listY + row * ROW_HEIGHT;
            boolean selected = entry.path().equals(selectedFile);
            boolean hovered = mouseX >= listX && mouseX < listX + listW
                    && mouseY >= y && mouseY < y + ROW_HEIGHT;

            if (selected || hovered) {
                graphics.fill(listX + 1, y, listX + listW - 1, y + ROW_HEIGHT - 1,
                        selected ? ROW_SELECTED : ROW_HOVER);
            }
            if (selected) {
                graphics.fill(listX + 1, y, listX + 3, y + ROW_HEIGHT - 1, ACCENT);
            }

            String prefix = entry.directory() ? "[DIR] " : "[" + extension(entry.path()) + "] ";
            int prefixColor = entry.directory() ? ACCENT : GREEN;
            graphics.drawString(font, prefix, listX + 8, y + 7, prefixColor);
            String name = fit(entry.path().getFileName().toString(), listW - 130);
            graphics.drawString(font, name, listX + 49, y + 7, TEXT);

            if (!entry.directory()) {
                String size = fileSize(entry.path());
                int sizeX = listX + listW - font.width(size) - 9;
                graphics.drawString(font, size, sizeX, y + 7, MUTED);
            }
        }

        if (entries.size() > visibleRows) {
            drawScrollbar(graphics, listX + listW - 4, listY, listH,
                    entries.size(), visibleRows, scrollOffset);
        }

        graphics.fill(panelX + 10, footerY, panelX + panelW - 10, footerY + 1, BORDER);
        String selectedName = selectedFile == null
                ? tr("richchat.file_browser.no_file")
                : tr("richchat.file_browser.selected", selectedFile.getFileName().toString());
        graphics.drawString(font, fit(selectedName, Math.max(80, chooseButton.x - panelX - 28)),
                panelX + 10, footerY + 4, TEXT);
        int statusColor = statusIsError ? RED : MUTED;
        graphics.drawString(font, fit(status, Math.max(80, chooseButton.x - panelX - 28)),
                panelX + 10, footerY + 17, statusColor);

        drawButton(graphics, chooseButton, tr("richchat.file_browser.choose"),
                mouseX, mouseY, selectedFile != null);
        drawButton(graphics, cancelButton, tr("gui.cancel"), mouseX, mouseY, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawButton(GuiGraphics graphics, Rect rect, String label,
                            int mouseX, int mouseY, boolean primary) {
        boolean hovered = mouseX >= rect.x && mouseX < rect.x + rect.width
                && mouseY >= rect.y && mouseY < rect.y + rect.height;
        int background = primary ? (hovered ? 0xFFE6B16F : ACCENT)
                : hovered ? ROW_HOVER : ROW;
        int accent = primary ? 0xFFFFD7A2 : hovered ? 0xFF889185 : BORDER;
        graphics.fill(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, background);
        graphics.fill(rect.x, rect.y, rect.x + 2, rect.y + rect.height, accent);
        graphics.fill(rect.x + 2, rect.y, rect.x + rect.width, rect.y + 1, BORDER);
        graphics.fill(rect.x + 2, rect.y + rect.height - 1,
                rect.x + rect.width, rect.y + rect.height, BORDER);
        graphics.drawCenteredString(font, fit(label, rect.width - 8),
                rect.x + rect.width / 2, rect.y + (rect.height - font.lineHeight) / 2,
                primary ? 0xFF171A18 : TEXT);
    }

    private void drawScrollbar(GuiGraphics graphics, int x, int y, int height,
                               int total, int visible, int offset) {
        graphics.fill(x, y, x + 3, y + height, 0xFF151817);
        int thumbHeight = Math.max(12, height * visible / Math.max(1, total));
        int thumbY = y + (height - thumbHeight) * offset / Math.max(1, total - visible);
        graphics.fill(x, thumbY, x + 3, thumbY + thumbHeight, ACCENT);
    }

    private String fit(String value, int maxWidth) {
        String text = value == null ? "" : value;
        int allowed = Math.max(0, maxWidth);
        while (!text.isEmpty() && font.width(text) > allowed) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "FILE" : name.substring(dot + 1).toUpperCase(Locale.ROOT);
    }

    private String fileSize(Path path) {
        try {
            long bytes = Files.size(path);
            if (bytes >= 1024L * 1024L) {
                return String.format(Locale.ROOT, "%.1f MiB", bytes / (1024.0D * 1024.0D));
            }
            return Math.max(1L, bytes / 1024L) + " KiB";
        } catch (IOException | SecurityException ignored) {
            return "?";
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (contains(upButton, mouseX, mouseY)) {
                navigateUp();
                return true;
            }
            if (contains(goButton, mouseX, mouseY)) {
                navigateFromInput();
                return true;
            }
            if (contains(chooseButton, mouseX, mouseY)) {
                chooseFile();
                return true;
            }
            if (contains(cancelButton, mouseX, mouseY)) {
                onClose();
                return true;
            }
            if (mouseX >= listX && mouseX < listX + listW
                    && mouseY >= listY && mouseY < listY + listH) {
                int row = (int) ((mouseY - listY) / ROW_HEIGHT);
                int index = scrollOffset + row;
                if (index >= 0 && index < entries.size()) {
                    FileEntry entry = entries.get(index);
                    if (entry.directory()) {
                        refreshDirectory(entry.path(), true);
                    } else {
                        selectedFile = entry.path();
                        setStatus(tr("richchat.file_browser.ready"), false);
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean contains(Rect rect, double x, double y) {
        return x >= rect.x && x < rect.x + rect.width
                && y >= rect.y && y < rect.y + rect.height;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double deltaX, double deltaY) {
        if (mouseX >= listX && mouseX < listX + listW
                && mouseY >= listY && mouseY < listY + listH && deltaY != 0.0D) {
            int visibleRows = Math.max(1, listH / ROW_HEIGHT);
            int maxOffset = Math.max(0, entries.size() - visibleRows);
            scrollOffset = Math.max(0, Math.min(maxOffset,
                    scrollOffset + (deltaY < 0 ? 3 : -3)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (pathInput != null && pathInput.isFocused() && keyCode == GLFW.GLFW_KEY_ENTER) {
            navigateFromInput();
            return true;
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

    private record FileEntry(Path path, boolean directory) {}
    private record Rect(int x, int y, int width, int height) {}
}
