package com.mingdai.savegit.gui;

import com.mingdai.savegit.snapshot.Commit;
import com.mingdai.savegit.snapshot.SnapshotRepository;
import com.mingdai.savegit.util.L10n;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.KeyCodes;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 编辑快照备注。备注不参与提交哈希，可随时修改。
 */
public class GuiEditNote extends GuiSaveGitScreen {
    private final Commit commit;
    private final Path worldDir;
    private final Screen parent;
    private GuiTextFieldGeneric noteField;
    private String status = "";

    public GuiEditNote(Commit commit, Path worldDir, Screen parent) {
        this.commit = commit;
        this.worldDir = worldDir;
        this.parent = parent;
        this.setTitle(L10n.tr("gsave.gui.title.edit_note", commit.shortId()));
    }

    @Override
    public void initGui() {
        super.initGui();

        int fieldWidth = Math.min(320, this.width - 80);
        this.noteField = new GuiTextFieldGeneric(this.width / 2 - fieldWidth / 2,
                this.height / 2 - 8, fieldWidth, 20, this.textRenderer);
        this.noteField.setMaxLength(120);
        this.noteField.setTextWrapper(this.commit.note == null ? "" : this.commit.note);
        this.addTextField(this.noteField, field -> true);

        int buttonWidth = 100;
        int y = this.height / 2 + 24;
        this.addButton(new ButtonGeneric(this.width / 2 - buttonWidth - 4, y, buttonWidth, 20,
                        L10n.tr("gsave.button.save")),
                (button, mouseButton) -> this.save());
        this.addButton(new ButtonGeneric(this.width / 2 + 4, y, buttonWidth, 20,
                        L10n.tr("gsave.button.cancel")),
                (button, mouseButton) -> this.goBack());
    }

    private void save() {
        String note = this.noteField.getTextWrapper().trim();
        try {
            if (new SnapshotRepository(this.worldDir).setNote(this.commit.id, note)) {
                this.commit.note = note;
                this.goBack();
            } else {
                this.status = L10n.tr("gsave.message.commit_missing", this.commit.shortId());
            }
        } catch (IOException e) {
            this.status = L10n.tr("gsave.message.note_failed", e.getMessage());
        }
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

        String label = L10n.tr("gsave.gui.label.note");
        this.drawString(drawContext, label,
                this.width / 2 - this.getStringWidth(label) / 2,
                this.height / 2 - 24, 0xFFB0B0B0);

        if (!this.status.isEmpty()) {
            this.drawString(drawContext, this.status,
                    this.width / 2 - this.getStringWidth(this.status) / 2,
                    this.height / 2 + 50, 0xFFFF6060);
        }
    }
}
