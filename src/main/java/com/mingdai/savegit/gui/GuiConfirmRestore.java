package com.mingdai.savegit.gui;

import com.mingdai.savegit.snapshot.Commit;
import com.mingdai.savegit.snapshot.SaveGitSnapshotManager;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.KeyCodes;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public class GuiConfirmRestore extends GuiSaveGitScreen {
    private final Commit commit;
    private final GuiSnapshotList parent;
    private boolean createSafetySnapshot = true;
    private WidgetCheckBoxSimple safetyCheckBox;

    public GuiConfirmRestore(Commit commit, GuiSnapshotList parent) {
        this.commit = commit;
        this.parent = parent;
        this.setTitle("确认恢复快照");
    }

    @Override
    public void initGui() {
        super.initGui();

        this.safetyCheckBox = new WidgetCheckBoxSimple(0, 0, "恢复前创建安全快照", true);
        this.safetyCheckBox.setPosition(
                this.width / 2 - this.safetyCheckBox.getWidth() / 2, this.height / 2 + 10);
        this.safetyCheckBox.setChangeListener(
                () -> this.createSafetySnapshot = this.safetyCheckBox.isChecked());
        this.addWidget(this.safetyCheckBox);

        int buttonWidth = 100;
        int y = this.height / 2 + 42;
        this.addButton(new ButtonGeneric(this.width / 2 - buttonWidth - 4, y, buttonWidth, 20, "确认恢复"),
                (button, mouseButton) -> {
                    if (this.createSafetySnapshot) {
                        this.mc.setScreen(null);
                        SaveGitSnapshotManager.requestRestore(this.mc, this.commit, true);
                    } else {
                        GuiBase.openGui(new GuiConfirmNoSafetyRestore(this.commit, this.parent));
                    }
                });
        this.addButton(new ButtonGeneric(this.width / 2 + 4, y, buttonWidth, 20, "取消"),
                (button, mouseButton) -> this.cancel());
    }

    @Override
    public boolean onKeyTyped(int keyCode, int scanCode, int modifiers) {
        // 按 ESC 视为取消，返回快照列表
        if (keyCode == KeyCodes.KEY_ESCAPE) {
            this.cancel();
            return true;
        }
        return super.onKeyTyped(keyCode, scanCode, modifiers);
    }

    private void cancel() {
        if (this.parent != null) {
            GuiBase.openGui(this.parent);
        } else {
            GuiBase.openGui(new GuiSnapshotList());
        }
    }

    @Override
    protected void drawContents(DrawContext drawContext, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(drawContext, mouseX, mouseY, partialTicks);

        List<String> lines = new ArrayList<>();
        lines.add("即将恢复到快照 #" + this.commit.shortId());
        lines.add("时间: " + GuiSnapshotList.formatTime(this.commit.timestamp));
        lines.add("消息: " + (this.commit.message == null ? "" : this.commit.message));
        lines.add("");
        lines.add("恢复前会自动创建一个安全快照，取消勾选将跳过此步骤。");
        lines.add("世界将先返回标题界面，恢复过程中请勿关闭游戏。");

        int startY = this.height / 2 - lines.size() * 6 - 26;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int color = i == 0 ? 0xFFFFFFFF : 0xFFB0B0B0;
            this.drawString(drawContext, line,
                    this.width / 2 - this.getStringWidth(line) / 2, startY + i * 12, color);
        }
    }
}
