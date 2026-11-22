package com.mingdai.savegit.preview;

import com.mingdai.savegit.snapshot.ChunkPreviewMap;
import com.mingdai.savegit.snapshot.ChunkSelection;
import com.mingdai.savegit.util.L10n;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 预览模式的左下角总览:图例、各状态数量、范围与操作提示。
 */
public final class PreviewHudRenderer {
    private static final int LINE_HEIGHT = 11;
    private static final int PADDING = 4;
    private static final int SWATCH_SIZE = 7;

    private static final int COLOR_TITLE = 0xFFFFFFFF;
    private static final int COLOR_TEXT = 0xFFC8C8C8;
    private static final int COLOR_DIM = 0xFF9A9A9A;
    private static final int COLOR_MODIFIED = 0xFFFFB020;
    private static final int COLOR_DELETED = 0xFFFF4040;
    private static final int COLOR_UNCHANGED = 0xFF6FCF4A;

    private PreviewHudRenderer() {
    }

    public static void render(DrawContext drawContext) {
        PreviewManager manager = PreviewManager.getInstance();
        if (!manager.isActive()) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        PreviewSession session = manager.session();
        if (session == null || mc.textRenderer == null) {
            return;
        }

        List<Line> lines = buildLines(mc, manager, session);
        if (lines.isEmpty()) {
            return;
        }

        int textWidth = 0;
        for (Line line : lines) {
            textWidth = Math.max(textWidth,
                    mc.textRenderer.getWidth(line.text()) + (line.swatchColor() != 0 ? SWATCH_SIZE + 4 : 0));
        }
        int width = textWidth + PADDING * 2;
        int height = lines.size() * LINE_HEIGHT + PADDING * 2;
        int x = 6;
        int y = drawContext.getScaledWindowHeight() - height - 6;

        drawContext.fill(x - PADDING, y - PADDING, x + width, y + height - PADDING + 1, 0xB0000000);

        int textY = y;
        for (Line line : lines) {
            int textX = x;
            if (line.swatchColor() != 0) {
                drawContext.fill(x, textY + 2, x + SWATCH_SIZE, textY + 2 + SWATCH_SIZE, line.swatchColor());
                textX += SWATCH_SIZE + 4;
            }
            drawContext.drawTextWithShadow(mc.textRenderer, Text.literal(line.text()), textX, textY, line.color());
            textY += LINE_HEIGHT;
        }
    }

    private static List<Line> buildLines(MinecraftClient mc, PreviewManager manager,
                                         PreviewSession session) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(L10n.tr("gsave.preview.hud.title", session.commit().shortId()),
                COLOR_TITLE, 0));

        String dimension = mc.world == null ? "" : mc.world.getRegistryKey().getValue().toString();
        lines.add(new Line(L10n.tr("gsave.preview.hud.dimension", dimension), COLOR_DIM, 0));

        switch (session.status()) {
            case COMPUTING -> lines.add(new Line(L10n.tr("gsave.preview.hud.computing"), COLOR_TEXT, 0));
            case FAILED -> lines.add(new Line(L10n.tr("gsave.preview.hud.failed",
                    session.error() == null ? "?" : session.error()), COLOR_DELETED, 0));
            case READY -> {
                ChunkPreviewMap map = session.previewMap();
                Map<ChunkPreviewMap.State, Integer> counts =
                        map == null ? Map.of() : map.counts(dimension);
                lines.add(new Line(L10n.tr("gsave.preview.hud.modified",
                        String.valueOf(counts.getOrDefault(ChunkPreviewMap.State.MODIFIED, 0))),
                        COLOR_MODIFIED, COLOR_MODIFIED));
                lines.add(new Line(L10n.tr("gsave.preview.hud.deleted",
                        String.valueOf(counts.getOrDefault(ChunkPreviewMap.State.DELETED, 0))),
                        COLOR_DELETED, COLOR_DELETED));
                lines.add(new Line(L10n.tr("gsave.preview.hud.unchanged",
                        String.valueOf(counts.getOrDefault(ChunkPreviewMap.State.UNCHANGED, 0))),
                        COLOR_UNCHANGED, COLOR_UNCHANGED));

                ChunkSelection selection = manager.selection();
                if (selection == null) {
                    lines.add(new Line(L10n.tr("gsave.preview.hud.range_all"), COLOR_DIM, 0));
                } else {
                    int side = selection.radiusChunks() * 2 - 1;
                    lines.add(new Line(L10n.tr("gsave.preview.hud.range_chunks",
                            String.valueOf(selection.radiusChunks()),
                            String.valueOf(side), String.valueOf((long) side * side)), COLOR_DIM, 0));
                }
            }
        }

        lines.add(new Line(L10n.tr("gsave.preview.hud.legend"), COLOR_DIM, 0));
        lines.add(new Line(L10n.tr("gsave.preview.hud.instructions"), COLOR_TEXT, 0));
        return lines;
    }

    private record Line(String text, int color, int swatchColor) {
    }
}
