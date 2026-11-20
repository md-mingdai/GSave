package com.mingdai.savegit.gui;

import com.mingdai.savegit.SaveGitMod;
import com.mingdai.savegit.compat.ClientCompat;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

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
    private final Map<String, Entry> entries = new HashMap<>();
    private final Set<String> missing = new HashSet<>();
    private final String texturePrefix = Integer.toHexString(System.identityHashCode(this));

    public record Entry(Identifier id, int width, int height) {
    }

    /**
     * 加载截图并等比缩放到不超过 maxWidth×maxHeight 的尺寸，保持宽高比。
     * 调用方按返回的 width/height 原尺寸绘制即可，完整显示且不会被裁剪。
     */
    public Entry getScaled(Path file, String key, int maxWidth, int maxHeight) {
        return this.load(file, key, Math.max(1, maxWidth), Math.max(1, maxHeight));
    }

    private Entry load(Path file, String key, int maxWidth, int maxHeight) {
        String cacheKey = key + ":" + maxWidth + "x" + maxHeight;
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
            if (source.getWidth() > maxWidth || source.getHeight() > maxHeight) {
                image = scaleToFit(source, maxWidth, maxHeight);
                source.close();
            } else {
                image = source;
            }

            String path = "shots/" + key + "_" + maxWidth + "x" + maxHeight + "_" + this.texturePrefix;
            Identifier id = Identifier.fromNamespaceAndPath(SaveGitMod.MOD_ID, path.toLowerCase(Locale.ROOT));
            var texture = ClientCompat.createTexture(image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
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

    private static NativeImage scaleToFit(NativeImage source, int maxWidth, int maxHeight) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return new NativeImage(1, 1, false);
        }

        float scale = Math.min(maxWidth / (float) sourceWidth,
                maxHeight / (float) sourceHeight);
        int width = Math.max(1, (int) (sourceWidth * scale));
        int height = Math.max(1, (int) (sourceHeight * scale));

        NativeImage target = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            int sourceY = Math.min(sourceHeight - 1, y * sourceHeight / height);
            for (int x = 0; x < width; x++) {
                int sourceX = Math.min(sourceWidth - 1, x * sourceWidth / width);
                ClientCompat.setPixel(target, x, y, ClientCompat.getPixel(source, sourceX, sourceY));
            }
        }
        return target;
    }

    @Override
    public void close() {
        var textureManager = Minecraft.getInstance().getTextureManager();
        for (Entry entry : this.entries.values()) {
            textureManager.release(entry.id());
        }
        this.entries.clear();
        this.missing.clear();
    }
}
