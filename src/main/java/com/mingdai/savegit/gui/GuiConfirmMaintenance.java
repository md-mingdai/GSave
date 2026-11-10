package com.mingdai.savegit.gui;

import com.mingdai.savegit.snapshot.RepositoryMaintenance;
import com.mingdai.savegit.util.L10n;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.KeyCodes;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import java.nio.file.Path;
import java.util.List;

/**
 * 清理未引用数据前的确认界面。
 */
public class GuiConfirmMaintenance extends GuiSaveGitScreen {
    private final Path worldDir;
    private final String worldDisplayName;
    private final Screen parent;

    public GuiConfirmMaintenance(Path worldDir, String worldDisplayName, Screen parent) {
        this.worldDir = worldDir;
        this.worldDisplayName = worldDisplayName;
        this.parent = parent;
        this.setTitle(L10n.tr("gsave.confirm.gc.title"));
    }

    @Override
    public void initGui() {
        super.initGui();

        int buttonWidth = 110;
        int y = this.height / 2 + 26;
        this.addButton(new ButtonGeneric(this.width / 2 - buttonWidth - 4, y, buttonWidth, 20,
                        L10n.tr("gsave.button.confirm_delete")),
                (button, mouseButton) -> {
                    RepositoryMaintenance.startGc(this.worldDir);
                    GuiBase.openGui(this.parent != null ? this.parent
                            : new GuiRepositoryMaintenance(this.worldDir,
                            this.worldDisplayName, null));
                });
        this.addButton(new ButtonGeneric(this.width / 2 + 4, y, buttonWidth, 20,
                        L10n.tr("gsave.button.cancel")),
                (button, mouseButton) -> this.goBack());
    }

    @Override
    public boolean onKeyTyped(int keyCode, int scanCode, int modifiers) {
        if (keyCode == KeyCodes.KEY_ESCAPE) {
            this.goBack();
            return true;
        }
        return super.onKeyTyped(keyCode, scanCode, modifiers);
    }

    private void goBack() {
        if (this.parent != null) {
            GuiBase.openGui(this.parent);
        } else {
            this.close();
        }
    }

    @Override
    protected void drawContents(DrawContext drawContext, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(drawContext, mouseX, mouseY, partialTicks);

        List<String> lines = List.of(
                L10n.tr("gsave.confirm.gc.message.1"),
                L10n.tr("gsave.confirm.gc.message.2"),
                "",
                L10n.tr("gsave.confirm.gc.message.3")
        );

        int startY = this.height / 2 - lines.size() * 6 - 16;
        com.mingdai.savegit.gui.theme.GuiTheme.drawDialogPanel(drawContext,
                this.textRenderer, this.width / 2, startY, lines);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int color = i == 0 ? 0xFFFF6060 : 0xFFB0B0B0;
            this.drawString(drawContext, line,
                    this.width / 2 - this.getStringWidth(line) / 2, startY + i * 12, color);
        }
    }
}
