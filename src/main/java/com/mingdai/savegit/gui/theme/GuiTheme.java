package com.mingdai.savegit.gui.theme;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * GSave 界面统一视觉：深色半透明面板、卡片、胶囊按钮与滚动条。
 * 全部使用填充绘制，不依赖任何材质图集。
 */
public final class GuiTheme {
    public static final int PANEL_BG = 0xB4101418;
    public static final int PANEL_BORDER = 0x35FFFFFF;
    public static final int HEADER_BG = 0xC013181D;
    public static final int CARD_BG = 0x8C14181D;
    public static final int CARD_BG_HOVER = 0xC81E242B;
    public static final int CARD_BG_SELECTED = 0xD0232E3A;
    public static final int CARD_BORDER = 0x28FFFFFF;
    public static final int CARD_BORDER_HOVER = 0x55FFFFFF;

    public static final int ACCENT = 0xFF4F9EEA;
    public static final int ACCENT_DARK = 0xFF2A5E8F;
    public static final int SUCCESS = 0xFF4CAF7D;
    public static final int WARNING = 0xFFE0A74C;
    public static final int DANGER = 0xFFD9534F;

    public static final int TEXT_MAIN = 0xFFFFFFFF;
    public static final int TEXT_DIM = 0xFF9AA4B0;
    public static final int TEXT_FAINT = 0xFF6A7480;

    public static final int BUTTON_HEIGHT = 20;

    public enum ButtonStyle {
        PRIMARY(0xFF2F6EA8, 0xFF3D84C6, 0xFF1E4A72),
        SECONDARY(0xFF3A4149, 0xFF4B545E, 0xFF272C32),
        DANGER(0xFF8C3330, 0xFFAC403C, 0xFF5E2220),
        DISABLED(0xFF24282D, 0xFF24282D, 0xFF1A1D21);

        final int fill;
        final int hover;
        final int border;

        ButtonStyle(int fill, int hover, int border) {
            this.fill = fill;
            this.hover = hover;
            this.border = border;
        }
    }

    private GuiTheme() {
    }

    public static void drawPanel(DrawContext context, int x1, int y1, int x2, int y2) {
        context.fill(x1, y1, x2, y2, PANEL_BG);
        context.fill(x1, y1, x2, y1 + 1, PANEL_BORDER);
        context.fill(x1, y2 - 1, x2, y2, PANEL_BORDER);
        context.fill(x1, y1, x1 + 1, y2, PANEL_BORDER);
        context.fill(x2 - 1, y1, x2, y2, PANEL_BORDER);
    }

    public static void drawHeader(DrawContext context, int x1, int y1, int x2, int y2) {
        context.fill(x1, y1, x2, y2, HEADER_BG);
        context.fill(x1, y2 - 1, x2, y2, ACCENT_DARK);
        context.fill(x1, y1, x2, y1 + 1, 0x40FFFFFF);
    }

    public static void drawCard(DrawContext context, int x1, int y1, int x2, int y2,
                                boolean hovered, boolean selected) {
        int bg = selected ? CARD_BG_SELECTED : (hovered ? CARD_BG_HOVER : CARD_BG);
        int border = hovered || selected ? CARD_BORDER_HOVER : CARD_BORDER;
        context.fill(x1, y1, x2, y2, bg);
        context.fill(x1, y1, x2, y1 + 1, border);
        context.fill(x1, y2 - 1, x2, y2, border);
        context.fill(x1, y1, x1 + 1, y2, border);
        context.fill(x2 - 1, y1, x2, y2, border);
        if (selected) {
            context.fill(x1, y1, x1 + 2, y2, ACCENT);
        }
    }

    public static void drawThumbnailFrame(DrawContext context, int x1, int y1, int x2, int y2,
                                          boolean hovered) {
        context.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xFF0A0C0E);
        context.fill(x1 - 2, y1 - 2, x2 + 2, y2 + 2, hovered ? 0x60FFFFFF : 0x30FFFFFF);
        context.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xFF0A0C0E);
    }

    public static void drawDivider(DrawContext context, int x1, int y, int x2, int color) {
        context.fill(x1, y, x2, y + 1, color);
    }

    /** 绘制彩色小标签，返回占用宽度。 */
    public static int drawChip(DrawContext context, TextRenderer font, int x, int y,
                               String label, int textColor, int fillColor) {
        int width = font.getWidth(label) + 8;
        context.fill(x, y, x + width, y + 11, fillColor);
        context.drawTextWithShadow(font, label, x + 4, y + 2, textColor);
        return width;
    }

    public static void drawButton(DrawContext context, TextRenderer font,
                                  int x, int y, int width, int height,
                                  String label, ButtonStyle style, boolean hovered) {
        boolean enabled = style != ButtonStyle.DISABLED;
        int fill = hovered && enabled ? style.hover : style.fill;
        context.fill(x, y, x + width, y + height, fill);
        context.fill(x, y, x + width, y + 1, style.border);
        context.fill(x, y + height - 1, x + width, y + height, style.border);
        context.fill(x, y, x + 1, y + height, style.border);
        context.fill(x + width - 1, y, x + width, y + height, style.border);
        int color = enabled ? TEXT_MAIN : 0xFF70767C;
        int textX = x + (width - font.getWidth(label)) / 2;
        context.drawTextWithShadow(font, label, textX, y + (height - 8) / 2, color);
    }

    public static void drawScrollbar(DrawContext context, int x, int top, int bottom,
                                     int scrollOffset, int maxScroll) {
        context.fill(x, top, x + 4, bottom, 0x40000000);
        if (maxScroll <= 0) {
            return;
        }
        int visibleHeight = bottom - top;
        int contentHeight = visibleHeight + maxScroll;
        int barHeight = Math.max(20, visibleHeight * visibleHeight / contentHeight);
        int barY = top + (visibleHeight - barHeight) * scrollOffset / maxScroll;
        context.fill(x, barY, x + 4, barY + barHeight, 0xFFAAAAAA);
    }

    public static void drawEmptyState(DrawContext context, TextRenderer font,
                                      int centerX, int centerY, String title, String subtitle) {
        int width = Math.max(font.getWidth(title), font.getWidth(subtitle));
        int x = centerX - width / 2;
        context.drawTextWithShadow(font, title, centerX - font.getWidth(title) / 2,
                centerY - 10, TEXT_DIM);
        if (subtitle != null && !subtitle.isEmpty()) {
            context.drawTextWithShadow(font, subtitle, centerX - font.getWidth(subtitle) / 2,
                    centerY + 4, TEXT_FAINT);
        }
    }
}
