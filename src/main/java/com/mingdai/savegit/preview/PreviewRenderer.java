package com.mingdai.savegit.preview;

import com.mingdai.savegit.snapshot.ChunkPreviewMap;
import com.mingdai.savegit.snapshot.ChunkSelection;
import com.mojang.blaze3d.pipeline.RenderTarget;
import fi.dy.masa.malilib.event.RenderEventHandler;
import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.data.Color4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Vector4f;

//? if >=26.3 {
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
//?} else {
/*import com.mojang.blaze3d.buffers.GpuBufferSlice;
import org.joml.Matrix4fc;
*///?}

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 在世界中绘制区块状态:橙黄 = 将被修改,大红 = 将被删除,草绿 = 保持不变。
 * 只在区块表层绘制一层一像素高的色带与边框,贴合地形表面而不是漂浮在空中。
 */
public final class PreviewRenderer implements IRenderer {
    private static final int RENDER_RADIUS_CHUNKS = 12;
    private static final int MAX_RECTS = 768;

    private static final Color4f MODIFIED_FILL = new Color4f(1.0F, 0.70F, 0.05F, 0.22F);
    private static final Color4f MODIFIED_LINE = new Color4f(1.0F, 0.72F, 0.10F, 0.90F);
    private static final Color4f DELETED_FILL = new Color4f(1.0F, 0.15F, 0.15F, 0.25F);
    private static final Color4f DELETED_LINE = new Color4f(1.0F, 0.20F, 0.20F, 0.92F);
    private static final Color4f UNCHANGED_FILL = new Color4f(0.40F, 0.80F, 0.25F, 0.14F);
    private static final Color4f UNCHANGED_LINE = new Color4f(0.45F, 0.85F, 0.28F, 0.65F);

    private final Map<Long, Integer> surfaceCache = new HashMap<>();
    private PreviewSession cachedSession;
    private String cachedDimension;

    public static void register() {
        RenderEventHandler.getInstance().registerWorldLastRenderer(new PreviewRenderer());
    }

    //? if >=26.3 {
    @Override
    public void onRenderWorldLast(RenderTarget fb, CameraRenderState cameraState, Frustum culling,
                                  RenderBuffers buffers, GpuBufferSlice terrainFog, Vector4f fogColor,
                                  ProfilerFiller profiler) {
        this.renderPreview();
    }
    //?} else {
    /*@Override
    public void onRenderWorldLast(RenderTarget fb, Matrix4fc modelViewMatrix,
                                  CameraRenderState cameraState, Frustum culling,
                                  RenderBuffers buffers, GpuBufferSlice terrainFog,
                                  Vector4f fogColor, ProfilerFiller profiler) {
        this.renderPreview();
    }
    *///?}

    private void renderPreview() {
        PreviewManager manager = PreviewManager.getInstance();
        if (!manager.isActive()) {
            return;
        }
        ChunkPreviewMap map = manager.previewMap();
        if (map == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        String dimension = mc.level.dimension().identifier().toString();
        Map<ChunkPreviewMap.ChunkPos, ChunkPreviewMap.State> cells = map.forDimension(dimension);
        if (cells.isEmpty()) {
            return;
        }
        PreviewSession session = manager.session();
        if (session != this.cachedSession || !dimension.equals(this.cachedDimension)) {
            this.surfaceCache.clear();
            this.cachedSession = session;
            this.cachedDimension = dimension;
        }

        net.minecraft.world.entity.Entity cameraEntity = mc.getCameraEntity();
        double cameraX = cameraEntity != null ? cameraEntity.getX() : mc.player.getX();
        double cameraZ = cameraEntity != null ? cameraEntity.getZ() : mc.player.getZ();
        int cameraChunkX = Mth.floor(cameraX) >> 4;
        int cameraChunkZ = Mth.floor(cameraZ) >> 4;

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

        Map<Integer, Map<ChunkPreviewMap.ChunkPos, ChunkPreviewMap.State>> bySurface = new HashMap<>();
        for (Map.Entry<ChunkPreviewMap.ChunkPos, ChunkPreviewMap.State> entry : visible.entrySet()) {
            int surface = this.surfaceY(mc, entry.getKey().x(), entry.getKey().z());
            if (surface == Integer.MIN_VALUE) {
                continue;
            }
            bySurface.computeIfAbsent(surface, key -> new HashMap<>()).put(entry.getKey(), entry.getValue());
        }

        List<SurfaceRect> rects = new ArrayList<>();
        for (Map.Entry<Integer, Map<ChunkPreviewMap.ChunkPos, ChunkPreviewMap.State>> layer
                : bySurface.entrySet()) {
            for (PreviewRectangles.Rect rect : PreviewRectangles.merge(layer.getValue())) {
                rects.add(new SurfaceRect(rect, layer.getKey()));
            }
        }
        if (rects.isEmpty()) {
            return;
        }
        if (rects.size() > MAX_RECTS) {
            int center2X = cameraChunkX * 2;
            int center2Z = cameraChunkZ * 2;
            rects.sort(Comparator.comparingLong(entry -> {
                long dx = entry.rect().centerX() - center2X;
                long dz = entry.rect().centerZ() - center2Z;
                return dx * dx + dz * dz;
            }));
            rects = new ArrayList<>(rects.subList(0, MAX_RECTS));
        }

        for (SurfaceRect entry : rects) {
            PreviewRectangles.Rect rect = entry.rect();
            BlockPos pos1 = new BlockPos(rect.minX() * 16, entry.y(), rect.minZ() * 16);
            BlockPos pos2 = new BlockPos(rect.maxX() * 16 + 15, entry.y() + 1, rect.maxZ() * 16 + 15);
            RenderUtils.renderAreaSides(pos1, pos2, fillColor(rect.state()));
        }
        for (SurfaceRect entry : rects) {
            PreviewRectangles.Rect rect = entry.rect();
            BlockPos pos1 = new BlockPos(rect.minX() * 16, entry.y(), rect.minZ() * 16);
            BlockPos pos2 = new BlockPos(rect.maxX() * 16 + 15, entry.y() + 1, rect.maxZ() * 16 + 15);
            Color4f line = lineColor(rect.state());
            RenderUtils.renderAreaOutline(pos1, pos2, 1.5F, line, line, line);
        }
    }

    /** 区块表层方块的高度:取区块内 16×16 列高度图的最大值;未加载返回 MIN_VALUE。 */
    private int surfaceY(Minecraft mc, int chunkX, int chunkZ) {
        long key = ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
        Integer cached = this.surfaceCache.get(key);
        if (cached != null) {
            return cached;
        }

        int surface = Integer.MIN_VALUE;
        LevelChunk chunk = mc.level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk != null) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                    surface = Math.max(surface, top - 1);
                }
            }
        }
        this.surfaceCache.put(key, surface);
        return surface;
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

    private record SurfaceRect(PreviewRectangles.Rect rect, int y) {
    }
}
