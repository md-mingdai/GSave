package com.mingdai.savegit.gui;

import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import net.minecraft.client.gui.DrawContext;

/**
 * 不依赖材质图集的简易复选框。
 */
public class WidgetCheckBoxSimple extends WidgetBase {
    private static final int BOX_SIZE = 11;
    private static final int LABEL_GAP = 4;

    private final String label;
    private boolean checked;
    private Runnable changeListener;

    public WidgetCheckBoxSimple(int x, int y, String label, boolean checked) {
        super(x, y, 0, 0);
        this.label = label;
        this.checked = checked;
        this.width = BOX_SIZE + LABEL_GAP + this.getStringWidth(label);
        this.height = BOX_SIZE;
    }

    public boolean isChecked() {
        return this.checked;
    }

    public void setChangeListener(Runnable listener) {
        this.changeListener = listener;
    }

    @Override
    protected boolean onMouseClickedImpl(int mouseX, int mouseY, int mouseButton) {
        this.checked = !this.checked;
        if (this.changeListener != null) {
            this.changeListener.run();
        }
        return true;
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, boolean selected) {
        super.render(drawContext, mouseX, mouseY, selected);

        boolean hovered = this.isMouseOver(mouseX, mouseY);
        drawContext.fill(this.x, this.y, this.x + BOX_SIZE, this.y + BOX_SIZE,
                hovered ? 0xFFFFFFFF : 0xFFA0A0A0);
        drawContext.fill(this.x + 1, this.y + 1, this.x + BOX_SIZE - 1, this.y + BOX_SIZE - 1,
                this.checked ? 0xFF3CB371 : 0xFF101010);
        this.drawStringWithShadow(drawContext, this.x + BOX_SIZE + LABEL_GAP, this.y + 2,
                hovered ? 0xFFFFFFFF : 0xFFDDDDDD, this.label);
    }
}
