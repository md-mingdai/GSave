package com.mingdai.savegit.preview;

import com.mingdai.savegit.snapshot.ChunkPreviewMap;
import com.mingdai.savegit.snapshot.ChunkSelection;
import fi.dy.masa.malilib.event.RenderEventHandler;
import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.render.RenderUtils;
//? if >=1.21.5 {
import fi.dy.masa.malilib.util.data.Color4f;
//?} else {
/*import fi.dy.masa.malilib.util.Color4f;
*///?}
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 在世界中绘制区块状态:橙黄 = 将被修改,大红 = 将被删除,草绿 = 保持不变。
 * 使用相机附近的一层半透明色带 + 边框,方便在自由视角下辨认。
 */
public final class PreviewRenderer implements IRenderer {
    private static final int RENDER_RADIUS_CHUNKS = 12;
    private static final int MAX_RECTS = 768;
    private static final float BAND_HALF_HEIGHT = 2.0F;

    private static final Color4f MODIFIED_FILL = new Color4f(1.0F, 0.70F, 0.05F, 0.22F);
    private static final Color4f MODIFIED_LINE = new Color4f(1.0F, 0.72F, 0.10F, 0.90F);
    private static final Color4f DELETED_FILL = new Color4f(1.0F, 0.15F, 0.15F, 0.25F);
    private static final Color4f DELETED_LINE = new Color4f(1.0F, 0.20F, 0.20F, 0.92F);
    private static final Color4f UNCHANGED_FILL = new Color4f(0.40F, 0.80F, 0.25F, 0.14F);
    private static final Color4f UNCHANGED_LINE = new Color4f(0.45F, 0.85F, 0.28F, 0.65F);

    public static void register() {
        RenderEventHandler.getInstance().registerWorldLastRenderer(new PreviewRenderer());
    }

    @Override
    public void onRenderWorldLast(Matrix4f posMatrix, Matrix4f projMatrix) {
        PreviewManager manager = PreviewManager.getInstance();
        if (!manager.isActive()) {
            return;
        }
        ChunkPreviewMap map = manager.previewMap();
        if (map == null) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) {
            return;
        }

        String dimension = mc.world.getRegistryKey().getValue().toString();
        Map<ChunkPreviewMap.ChunkPos, ChunkPreviewMap.State> cells = map.forDimension(dimension);
        if (cells.isEmpty()) {
            return;
        }

        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        int cameraChunkX = MathHelper.floor(cameraPos.x) >> 4;
        int cameraChunkZ = MathHelper.floor(cameraPos.z) >> 4;

        Map<ChunkPreviewMap.ChunkPos, ChunkPreviewMap.State> visible = new HashMap<>();
        ChunkSelection selection = manager.selection();
        if (selection != null) {
            for (ChunkSelection.ChunkPos pos : selection.chunks()) {
                ChunkPreviewMap.State state = cells.get(new ChunkPreviewMap.ChunkPos(pos.x(), pos.z()));
                if (state != null) {
                    visible.put(new ChunkPreviewMap.ChunkPos(pos.x(), pos.z()), state);
                }
            }
        } else {
            for (int dx = -RENDER_RADIUS_CHUNKS; dx <= RENDER_RADIUS_CHUNKS; dx++) {
                for (int dz = -RENDER_RADIUS_CHUNKS; dz <= RENDER_RADIUS_CHUNKS; dz++) {
                    ChunkPreviewMap.ChunkPos pos =
                            new ChunkPreviewMap.ChunkPos(cameraChunkX + dx, cameraChunkZ + dz);
                    ChunkPreviewMap.State state = cells.get(pos);
                    if (state != null) {
                        visible.put(pos, state);
                    }
                }
            }
        }
        if (visible.isEmpty()) {
            return;
        }

        List<PreviewRectangles.Rect> rects = PreviewRectangles.merge(visible);
        if (rects.size() > MAX_RECTS) {
            int center2X = cameraChunkX * 2;
            int center2Z = cameraChunkZ * 2;
            rects.sort(Comparator.comparingLong(rect -> {
                long dx = rect.centerX() - center2X;
                long dz = rect.centerZ() - center2Z;
                return dx * dx + dz * dz;
            }));
            rects = new ArrayList<>(rects.subList(0, MAX_RECTS));
        }

        int bandY = MathHelper.floor(cameraPos.y);
        int minY = bandY - (int) BAND_HALF_HEIGHT;
        int maxY = bandY + (int) BAND_HALF_HEIGHT;

        for (PreviewRectangles.Rect rect : rects) {
            BlockPos pos1 = new BlockPos(rect.minX() * 16, minY, rect.minZ() * 16);
            BlockPos pos2 = new BlockPos(rect.maxX() * 16 + 15, maxY, rect.maxZ() * 16 + 15);
            //? if >=1.21.5 {
            RenderUtils.renderAreaSides(pos1, pos2, fillColor(rect.state()), posMatrix);
            //?} else {
            /*RenderUtils.renderAreaSides(pos1, pos2, fillColor(rect.state()), posMatrix, mc);
            *///?}
        }
        for (PreviewRectangles.Rect rect : rects) {
            BlockPos pos1 = new BlockPos(rect.minX() * 16, minY, rect.minZ() * 16);
            BlockPos pos2 = new BlockPos(rect.maxX() * 16 + 15, maxY, rect.maxZ() * 16 + 15);
            Color4f line = lineColor(rect.state());
            //? if >=1.21.5 {
            RenderUtils.renderAreaOutline(pos1, pos2, 1.5F, line, line, line);
            //?} else {
            /*RenderUtils.renderAreaOutline(pos1, pos2, 1.5F, line, line, line, mc);
            *///?}
        }
    }

    private static Color4f fillColor(ChunkPreviewMap.State state) {
        return switch (state) {
            case MODIFIED -> MODIFIED_FILL;
            case DELETED -> DELETED_FILL;
            case UNCHANGED -> UNCHANGED_FILL;
        };
    }

    private static Color4f lineColor(ChunkPreviewMap.State state) {
        return switch (state) {
            case MODIFIED -> MODIFIED_LINE;
            case DELETED -> DELETED_LINE;
            case UNCHANGED -> UNCHANGED_LINE;
        };
    }
}
