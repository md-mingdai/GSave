package com.mingdai.savegit.gui;

import com.mingdai.savegit.gui.theme.GuiTheme;
import com.mingdai.savegit.snapshot.RepositoryMaintenance;
import com.mingdai.savegit.util.L10n;
import com.mingdai.savegit.util.SnapshotTime;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.KeyCodes;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 从 gsave-exports 目录选择 zip 归档导入快照。
 */
public class GuiImportArchive extends GuiSaveGitScreen {
    private static final int LIST_TOP = 62;
    private static final int LIST_BOTTOM_MARGIN = 44;
    private static final int ROW_HEIGHT = 28;

    private final Path worldDir;
    private final String worldDisplayName;
    private final Screen parent;
    private final Screen afterImportReturn;
    private final Path exportsDir;

    private List<Path> archives = List.of();
    private Path selected;
    private int scrollOffset;

    private ButtonGeneric importButton;

    public GuiImportArchive(Path worldDir, String worldDisplayName, Screen parent) {
        this(worldDir, worldDisplayName, parent, parent);
    }

    public GuiImportArchive(Path worldDir, String worldDisplayName,
                            Screen parent, Screen afterImportReturn) {
        this.worldDir = worldDir;
        this.worldDisplayName = worldDisplayName;
        this.parent = parent;
        this.afterImportReturn = afterImportReturn;
        this.exportsDir = net.minecraft.client.MinecraftClient.getInstance()
                .runDirectory.toPath().resolve("gsave-exports");
        this.setTitle(L10n.tr("gsave.gui.title.import"));
    }

    @Override
    public void initGui() {
        super.initGui();
        this.refresh();

        int y = this.height - 28;
        this.importButton = new ButtonGeneric(20, y, 80, 20, L10n.tr("gsave.button.import"));
        this.addButton(this.importButton, (button, mouseButton) -> this.startImport());
        this.addButton(new ButtonGeneric(108, y, 60, 20, L10n.tr("gsave.button.refresh")),
                (button, mouseButton) -> this.refresh());

        int backWidth = 100;
        this.addButton(new ButtonGeneric(this.width - 20 - backWidth, y, backWidth, 20,
                        L10n.tr("gsave.button.back")),
                (button, mouseButton) -> this.goBack());
    }

    private void refresh() {
        List<Path> found = new ArrayList<>();
        if (Files.isDirectory(this.exportsDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(this.exportsDir, "*.zip")) {
                for (Path path : stream) {
                    if (Files.isRegularFile(path)) {
                        found.add(path);
                    }
                }
            } catch (IOException ignored) {
            }
        }
        found.sort(Comparator.comparingLong(GuiImportArchive::modifiedTime).reversed());
        this.archives = found;
        this.scrollOffset = 0;
        if (this.selected != null && !found.contains(this.selected)) {
            this.selected = null;
        }
    }

    private static long modifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private void startImport() {
        if (this.selected == null) {
            return;
        }
        RepositoryMaintenance.startImport(this.worldDir, this.selected);
        GuiBase.openGui(new GuiRepositoryMaintenance(
                this.worldDir, this.worldDisplayName, this.afterImportReturn));
    }

    private void goBack() {
        if (this.parent != null) {
            GuiBase.openGui(this.parent);
        } else {
            this.close();
        }
    }

    @Override
    public boolean onKeyTyped(int keyCode, int scanCode, int modifiers) {
        if (keyCode == KeyCodes.KEY_ESCAPE) {
            this.goBack();
            return true;
        }
        return super.onKeyTyped(keyCode, scanCode, modifiers);
    }

    @Override
    protected void drawContents(DrawContext drawContext, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(drawContext, mouseX, mouseY, partialTicks);

        if (this.importButton != null) {
            this.importButton.setEnabled(this.selected != null);
        }

        int left = 20;
        int right = this.width - 20;
        int top = 40;
        int bottom = this.height - LIST_BOTTOM_MARGIN;
        GuiTheme.drawPanel(drawContext, left, top, right, bottom);

        String hint = L10n.tr("gsave.gui.message.import_hint", this.exportsDir.toString());
        this.drawString(drawContext, this.textRenderer.trimToWidth(hint, right - left - 20),
                left + 10, top + 10, GuiTheme.TEXT_DIM);

        int listTop = Math.max(LIST_TOP, top + 28);
        if (this.archives.isEmpty()) {
            GuiTheme.drawEmptyState(drawContext, this.textRenderer, this.width / 2,
                    (listTop + bottom) / 2, L10n.tr("gsave.gui.message.import_empty"), null);
            return;
        }

        int visibleHeight = bottom - listTop - 4;
        int contentHeight = this.archives.size() * ROW_HEIGHT;
        int maxScroll = Math.max(0, contentHeight - visibleHeight);
        this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset));

        drawContext.enableScissor(left + 1, listTop, right - 1, bottom - 1);
        int first = this.scrollOffset / ROW_HEIGHT;
        int last = Math.min(this.archives.size(), (this.scrollOffset + visibleHeight) / ROW_HEIGHT + 1);
        for (int i = first; i < last; i++) {
            Path archive = this.archives.get(i);
            int rowY = listTop + i * ROW_HEIGHT - this.scrollOffset;
            boolean hovered = mouseX >= left + 1 && mouseX < right - 1
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            boolean isSelected = archive.equals(this.selected);
            GuiTheme.drawCard(drawContext, left + 4, rowY, right - 4, rowY + ROW_HEIGHT - 2,
                    hovered, isSelected);

            String name = this.textRenderer.trimToWidth(
                    archive.getFileName().toString(), right - left - 180);
            this.drawString(drawContext, name, left + 12, rowY + 5, GuiTheme.TEXT_MAIN);

            String meta = L10n.tr("gsave.gui.value.archive_meta",
                    sizeText(archive), SnapshotTime.absolute(modifiedTime(archive)));
            this.drawString(drawContext, meta, left + 12, rowY + 15, GuiTheme.TEXT_FAINT);
        }
        drawContext.disableScissor();

        GuiTheme.drawScrollbar(drawContext, right - 8, listTop, bottom - 4,
                this.scrollOffset, maxScroll);
    }

    private static String sizeText(Path file) {
        try {
            return RepositoryMaintenance.formatBytes(Files.size(file));
        } catch (IOException e) {
            return "?";
        }
    }

    @Override
    public boolean onMouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (super.onMouseClicked(mouseX, mouseY, mouseButton)) {
            return true;
        }
        if (mouseButton != 0 || this.archives.isEmpty()) {
            return false;
        }

        int left = 20;
        int right = this.width - 20;
        int bottom = this.height - LIST_BOTTOM_MARGIN;
        int listTop = Math.max(LIST_TOP, 40 + 28);
        if (mouseY < listTop || mouseY >= bottom) {
            return false;
        }

        for (int i = 0; i < this.archives.size(); i++) {
            int rowY = listTop + i * ROW_HEIGHT - this.scrollOffset;
            if (mouseY >= rowY && mouseY < rowY + ROW_HEIGHT
                    && mouseX >= left + 4 && mouseX < right - 4) {
                Path clicked = this.archives.get(i);
                this.selected = clicked.equals(this.selected) ? null : clicked;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onMouseScrolled(int mouseX, int mouseY, double horizontalAmount, double verticalAmount) {
        if (super.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        if (verticalAmount != 0) {
            this.scrollOffset = Math.max(0, this.scrollOffset - (int) verticalAmount * ROW_HEIGHT);
            return true;
        }
        return false;
    }
}
