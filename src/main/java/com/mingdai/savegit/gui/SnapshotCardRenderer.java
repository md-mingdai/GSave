package com.mingdai.savegit.gui;

import com.mingdai.savegit.gui.theme.GuiTheme;
import com.mingdai.savegit.snapshot.Commit;
import com.mingdai.savegit.util.L10n;
import com.mingdai.savegit.util.SnapshotTime;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;

/**
 * 快照卡片行：缩略图 + ID/消息 + 绝对/相对时间 + 右侧动作按钮。
 * 游戏内列表与离线管理共用同一套绘制与命中测试。
 */
public final class SnapshotCardRenderer {
    public static final int CARD_HEIGHT = 44;
    public static final int THUMB_WIDTH = 64;
    public static final int THUMB_HEIGHT = 36;
    public static final int BUTTON_HEIGHT = 16;
    public static final int RESTORE_WIDTH = 46;
    public static final int DELETE_WIDTH = 46;
    public static final int PIN_WIDTH = 16;
    public static final int BUTTON_GAP = 4;
    public static final int SIDE_PADDING = 8;

    public enum Action {
        NONE,
        THUMBNAIL,
        PIN,
        RESTORE,
        DELETE,
        EDIT_NOTE
    }

    public record Layout(int x1, int y1, int x2, int y2,
                         int thumbX, int thumbY,
                         int buttonY, int pinX, int restoreX, int deleteX) {
    }

    private SnapshotCardRenderer() {
    }

    public static Layout layout(int listLeft, int listWidth, int rowY) {
        int x1 = listLeft;
        int x2 = listLeft + listWidth;
        int y1 = rowY;
        int y2 = rowY + CARD_HEIGHT;
        int thumbX = x1 + SIDE_PADDING;
        int thumbY = y1 + (CARD_HEIGHT - THUMB_HEIGHT) / 2;
        int buttonY = y1 + (CARD_HEIGHT - BUTTON_HEIGHT) / 2;
        int deleteX = x2 - SIDE_PADDING - DELETE_WIDTH;
        int restoreX = deleteX - BUTTON_GAP - RESTORE_WIDTH;
        int pinX = restoreX - BUTTON_GAP - PIN_WIDTH;
        return new Layout(x1, y1, x2, y2, thumbX, thumbY, buttonY, pinX, restoreX, deleteX);
    }

    public static Action hitTest(Layout layout, int mouseX, int mouseY,
                                 boolean hasThumbnail, boolean deleteEnabled) {
        if (mouseY < layout.y1() || mouseY >= layout.y2()) {
            return Action.NONE;
        }
        if (hasThumbnail
                && mouseX >= layout.thumbX() && mouseX < layout.thumbX() + THUMB_WIDTH
                && mouseY >= layout.thumbY() && mouseY < layout.thumbY() + THUMB_HEIGHT) {
            return Action.THUMBNAIL;
        }
        if (mouseX >= layout.pinX() && mouseX < layout.pinX() + PIN_WIDTH
                && mouseY >= layout.buttonY() && mouseY < layout.buttonY() + BUTTON_HEIGHT) {
            return Action.PIN;
        }
        if (mouseX >= layout.restoreX() && mouseX < layout.restoreX() + RESTORE_WIDTH
                && mouseY >= layout.buttonY() && mouseY < layout.buttonY() + BUTTON_HEIGHT) {
            return Action.RESTORE;
        }
        if (mouseX >= layout.deleteX() && mouseX < layout.deleteX() + DELETE_WIDTH
                && mouseY >= layout.buttonY() && mouseY < layout.buttonY() + BUTTON_HEIGHT) {
            return deleteEnabled ? Action.DELETE : Action.NONE;
        }
        if (isInsideCard(layout, mouseX, mouseY)) {
            return Action.EDIT_NOTE;
        }
        return Action.NONE;
    }

    public static boolean isInsideCard(Layout layout, int mouseX, int mouseY) {
        return mouseX >= layout.x1() && mouseX < layout.x2()
                && mouseY >= layout.y1() && mouseY < layout.y2();
    }

    public static void draw(DrawContext context, TextRenderer font, Layout layout, Commit commit,
                            SnapshotThumbnailCache.Entry thumbnail, boolean hasShot,
                            boolean pinned, boolean deleteEnabled,
                            boolean hovered, boolean selected, int mouseX, int mouseY) {
        GuiTheme.drawCard(context, layout.x1(), layout.y1(), layout.x2(), layout.y2(),
                hovered, selected);

        drawThumbnail(context, font, layout, thumbnail, hasShot, mouseX, mouseY);

        int textX = layout.thumbX() + THUMB_WIDTH + 10;
        int textWidth = Math.max(40, layout.pinX() - BUTTON_GAP - textX);

        String id = "#" + commit.shortId();
        context.drawTextWithShadow(font, id, textX, layout.y1() + 7, GuiTheme.ACCENT);
        int idWidth = font.getWidth(id);

        int messageX = textX + idWidth + 8;
        if (pinned) {
            context.drawTextWithShadow(font, "★", messageX, layout.y1() + 7, GuiTheme.WARNING);
            messageX += font.getWidth("★") + 6;
        }

        String message = commit.note != null && !commit.note.isEmpty()
                ? commit.note
                : L10n.commitMessage(commit.message);
        if (message.isEmpty()) {
            message = L10n.tr("gsave.gui.value.no_message");
        }
        message = font.trimToWidth(message, Math.max(20, textX + textWidth - messageX));
        context.drawTextWithShadow(font, message, messageX, layout.y1() + 7, GuiTheme.TEXT_MAIN);

        String time = SnapshotTime.full(commit.timestamp);
        time = font.trimToWidth(time, textWidth);
        context.drawTextWithShadow(font, time, textX, layout.y1() + 23, GuiTheme.TEXT_DIM);

        boolean pinHovered = isOver(layout.pinX(), layout.buttonY(), PIN_WIDTH, mouseX, mouseY);
        String pinLabel = pinned ? "★" : "☆";
        int pinColor = pinned ? GuiTheme.WARNING : (pinHovered ? GuiTheme.TEXT_MAIN : GuiTheme.TEXT_FAINT);
        context.drawTextWithShadow(font, pinLabel,
                layout.pinX() + (PIN_WIDTH - font.getWidth(pinLabel)) / 2,
                layout.buttonY() + 4, pinColor);

        GuiTheme.drawButton(context, font, layout.restoreX(), layout.buttonY(),
                RESTORE_WIDTH, BUTTON_HEIGHT, L10n.tr("gsave.button.restore"),
                GuiTheme.ButtonStyle.PRIMARY,
                isOver(layout.restoreX(), layout.buttonY(), RESTORE_WIDTH, mouseX, mouseY));

        GuiTheme.drawButton(context, font, layout.deleteX(), layout.buttonY(),
                DELETE_WIDTH, BUTTON_HEIGHT, L10n.tr("gsave.button.delete"),
                deleteEnabled ? GuiTheme.ButtonStyle.DANGER : GuiTheme.ButtonStyle.DISABLED,
                deleteEnabled
                        && isOver(layout.deleteX(), layout.buttonY(), DELETE_WIDTH, mouseX, mouseY));
    }

    private static void drawThumbnail(DrawContext context, TextRenderer font, Layout layout,
                                      SnapshotThumbnailCache.Entry thumbnail, boolean hasShot,
                                      int mouseX, int mouseY) {
        int x = layout.thumbX();
        int y = layout.thumbY();
        boolean hovered = isOver(x, y, THUMB_WIDTH, mouseX, mouseY);
        GuiTheme.drawThumbnailFrame(context, x, y, x + THUMB_WIDTH, y + THUMB_HEIGHT,
                hasShot && hovered);

        if (hasShot && thumbnail != null) {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, thumbnail.id(),
                    x, y, 0.0F, 0.0F, THUMB_WIDTH, THUMB_HEIGHT,
                    thumbnail.width(), thumbnail.height(),
                    thumbnail.width(), thumbnail.height());
        } else {
            context.fill(x, y, x + THUMB_WIDTH, y + THUMB_HEIGHT, 0xFF15181C);
            String none = L10n.tr("gsave.gui.value.none");
            context.drawTextWithShadow(font, none,
                    x + (THUMB_WIDTH - font.getWidth(none)) / 2,
                    y + (THUMB_HEIGHT - 8) / 2, GuiTheme.TEXT_FAINT);
        }
    }

    private static boolean isOver(int x, int y, int width, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
    }
}
