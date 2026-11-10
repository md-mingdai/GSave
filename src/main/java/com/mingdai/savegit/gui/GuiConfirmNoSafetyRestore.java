package com.mingdai.savegit.gui;

import com.mingdai.savegit.snapshot.Commit;
import com.mingdai.savegit.snapshot.SaveGitSnapshotManager;
import com.mingdai.savegit.util.L10n;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.KeyCodes;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/**
 * 取消“恢复前安全快照”后的二级确认。
 */
public class GuiConfirmNoSafetyRestore extends GuiSaveGitScreen {
    private final Commit commit;
    private final GuiSnapshotList parent;

    public GuiConfirmNoSafetyRestore(Commit commit, GuiSnapshotList parent) {
        this.commit = commit;
        this.parent = parent;
        this.setTitle(L10n.tr("gsave.gui.title.no_safety_restore"));
    }

    @Override
    public void initGui() {
        super.initGui();

        int buttonWidth = 160;
        int y = this.height / 2 + 40;
        this.addButton(new ButtonGeneric(this.width / 2 - buttonWidth - 4, y, buttonWidth, 20,
                        L10n.tr("gsave.button.i_know_what_im_doing")),
                (button, mouseButton) -> {
                    this.mc.setScreen(null);
                    SaveGitSnapshotManager.requestRestore(this.mc, this.commit, false);
                });
        this.addButton(new ButtonGeneric(this.width / 2 + 4, y, buttonWidth, 20,
                        L10n.tr("gsave.button.cancel")),
                (button, mouseButton) -> GuiBase.openGui(new GuiConfirmRestore(this.commit, this.parent)));
    }

    @Override
    public boolean onKeyTyped(int keyCode, int scanCode, int modifiers) {
        // 按 ESC 返回上一级确认界面
        if (keyCode == KeyCodes.KEY_ESCAPE) {
            GuiBase.openGui(new GuiConfirmRestore(this.commit, this.parent));
            return true;
        }
        return super.onKeyTyped(keyCode, scanCode, modifiers);
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
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int color = i == 0 ? 0xFFFF6060 : 0xFFFFDD55;
            this.drawString(drawContext, line,
                    this.width / 2 - this.getStringWidth(line) / 2, startY + i * 12, color);
        }
    }
}
