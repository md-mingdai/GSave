package com.mingdai.savegit.gui;

import com.mingdai.savegit.gui.theme.GuiTheme;
import com.mingdai.savegit.snapshot.Commit;
import com.mingdai.savegit.util.L10n;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/**
 * 取消“恢复前安全快照”后的二级确认。
 * 确认后执行调用方传入的恢复动作，取消则回到上一级（保留其选项状态）。
 */
public class GuiConfirmNoSafetyRestore extends GuiSaveGitScreen {
    private final Commit commit;
    private final GuiSaveGitScreen parent;
    private final Runnable onConfirm;

    public GuiConfirmNoSafetyRestore(Commit commit, GuiSaveGitScreen parent, Runnable onConfirm) {
        this.commit = commit;
        this.parent = parent;
        this.onConfirm = onConfirm;
        this.setTitle(L10n.tr("gsave.gui.title.no_safety_restore"));
    }

    @Override
    public void initGui() {
        super.initGui();

        int buttonWidth = 160;
        int y = this.height / 2 + 40;
        this.addButton(new ButtonGeneric(this.width / 2 - buttonWidth - 4, y, buttonWidth, 20,
                        L10n.tr("gsave.button.i_know_what_im_doing")),
                (button, mouseButton) -> this.onConfirm.run());
        this.addButton(new ButtonGeneric(this.width / 2 + 4, y, buttonWidth, 20,
                        L10n.tr("gsave.button.cancel")),
                (button, mouseButton) -> this.goBack());
    }

    private void goBack() {
        if (this.parent != null) {
            GuiBase.openGui(this.parent);
        } else {
            this.close();
        }
    }

    @Override
    protected boolean onEscapePressed() {
        this.goBack();
        return true;
    }

    @Override
    protected void drawContents(DrawContext drawContext, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(drawContext, mouseX, mouseY, partialTicks);

        List<String> lines = List.of(
                L10n.tr("gsave.gui.message.no_safety.1"),
                L10n.tr("gsave.gui.message.no_safety.2"),
                "",
                L10n.tr("gsave.gui.message.no_safety.3")
        );

        int startY = this.height / 2 - lines.size() * 6 - 30;
        GuiTheme.drawDialogPanel(drawContext, this.textRenderer, this.width / 2, startY, lines);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int color = i == 0 ? 0xFFFF6060 : 0xFFFFDD55;
            this.drawString(drawContext, line,
                    this.width / 2 - this.getStringWidth(line) / 2, startY + i * 12, color);
        }
    }
}
