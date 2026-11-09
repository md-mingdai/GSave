package com.mingdai.savegit.gui;

import com.mingdai.savegit.SaveGitMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 按需加载快照截图为纹理并缓存，界面关闭时释放。
 * 缩略图在加载时用最近邻方式缩小，避免为每张截图占用整张 1080p 的显存。
 */
public final class SnapshotThumbnailCache implements AutoCloseable {
    private static final int THUMB_MAX_WIDTH = 96;
    private static final int THUMB_MAX_HEIGHT = 54;

    private final Map<String, Entry> entries = new HashMap<>();
    private final Set<String> missing = new HashSet<>();
    private final String texturePrefix = Integer.toHexString(System.identityHashCode(this));

    public record Entry(Identifier id, int width, int height) {
    }

    /** 完整分辨率，用于大图查看。 */
    public Entry getFull(Path file, String key) {
        return this.load(file, key, false);
    }

    /** 缩小后的缩略图，用于列表。 */
    public Entry getThumbnail(Path file, String key) {
        return this.load(file, key, true);
    }

    private Entry load(Path file, String key, boolean thumbnail) {
        String cacheKey = thumbnail ? key + ":thumb" : key;
        if (this.missing.contains(cacheKey)) {
            return null;
        }
        Entry existing = this.entries.get(cacheKey);
        if (existing != null) {
            return existing;
        }
        if (!Files.isRegularFile(file)) {
            this.missing.add(cacheKey);
            return null;
        }

        NativeImage image = null;
        try (InputStream in = Files.newInputStream(file)) {
            NativeImage source = NativeImage.read(in);
            if (thumbnail) {
                image = downscale(source);
                source.close();
            } else {
                image = source;
            }

            String path = (thumbnail ? "shots/thumb/" : "shots/full/")
                    + key + "_" + this.texturePrefix;
            Identifier id = Identifier.of(SaveGitMod.MOD_ID, path.toLowerCase(Locale.ROOT));
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "savegit_shot", image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
            Entry entry = new Entry(id, image.getWidth(), image.getHeight());
            this.entries.put(cacheKey, entry);
            return entry;
        } catch (Throwable t) {
            if (image != null) {
                image.close();
            }
            this.missing.add(cacheKey);
            return null;
        }
    }

    private static NativeImage downscale(NativeImage source) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return new NativeImage(1, 1, false);
        }

        float scale = Math.min(THUMB_MAX_WIDTH / (float) sourceWidth,
                THUMB_MAX_HEIGHT / (float) sourceHeight);
        scale = Math.min(scale, 1.0F);
        int width = Math.max(1, (int) (sourceWidth * scale));
        int height = Math.max(1, (int) (sourceHeight * scale));

        NativeImage target = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            int sourceY = Math.min(sourceHeight - 1, y * sourceHeight / height);
            for (int x = 0; x < width; x++) {
                int sourceX = Math.min(sourceWidth - 1, x * sourceWidth / width);
                target.setColorArgb(x, y, source.getColorArgb(sourceX, sourceY));
            }
        }
        return target;
    }

    @Override
    public void close() {
        var textureManager = MinecraftClient.getInstance().getTextureManager();
        for (Entry entry : this.entries.values()) {
            textureManager.destroyTexture(entry.id());
        }
        this.entries.clear();
        this.missing.clear();
    }
}
