package com.mingdai.savegit.snapshot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mingdai.savegit.SaveGitMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Beta 世界文件哈希缓存：按 (size, mtime) 复用上次的 SHA-256，
 * 避免每次快照都重新读取未变化的大文件（结构文件、数据包等）。
 * 仅作为加速缓存，丢失或损坏只会导致重新哈希，不影响快照正确性。
 */
public final class WorldFileHashCache implements AutoCloseable {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public static final class Item {
        public long size;
        public long mtime;
        public String hash;
    }

    private final Path file;
    private final Map<String, Item> entries = new HashMap<>();
    private boolean dirty;

    private WorldFileHashCache(Path file) {
        this.file = file;
    }

    public static WorldFileHashCache open(Path saveGitDir) {
        WorldFileHashCache cache = new WorldFileHashCache(
                saveGitDir.resolve("cache").resolve("file-hashes.json"));
        cache.load();
        return cache;
    }

    private void load() {
        if (!Files.isRegularFile(this.file)) {
            return;
        }
        try {
            JsonObject root = GSON.fromJson(Files.readString(this.file, StandardCharsets.UTF_8),
                    JsonObject.class);
            if (root == null || !root.has("files") || !root.get("files").isJsonObject()) {
                return;
            }
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("files").entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject json = entry.getValue().getAsJsonObject();
                Item item = new Item();
                item.size = json.has("size") ? json.get("size").getAsLong() : -1L;
                item.mtime = json.has("mtime") ? json.get("mtime").getAsLong() : -1L;
                item.hash = json.has("hash") ? json.get("hash").getAsString() : null;
                this.entries.put(entry.getKey(), item);
            }
        } catch (IOException | RuntimeException e) {
            SaveGitMod.LOGGER.warn("读取文件哈希缓存失败，将重新计算", e);
        }
    }

    public String lookup(Path path, String relativePath) {
        Item item = this.entries.get(relativePath);
        if (item == null || item.hash == null) {
            return null;
        }
        try {
            if (Files.size(path) != item.size) {
                return null;
            }
            if (Files.getLastModifiedTime(path).toMillis() != item.mtime) {
                return null;
            }
            return item.hash;
        } catch (IOException e) {
            return null;
        }
    }

    public void remember(Path path, String relativePath, String hash) {
        try {
            Item item = new Item();
            item.size = Files.size(path);
            item.mtime = Files.getLastModifiedTime(path).toMillis();
            item.hash = hash;
            this.entries.put(relativePath, item);
            this.dirty = true;
        } catch (IOException ignored) {
        }
    }

    private void save() {
        JsonObject files = new JsonObject();
        for (Map.Entry<String, Item> entry : this.entries.entrySet()) {
            JsonObject json = new JsonObject();
            json.addProperty("size", entry.getValue().size);
            json.addProperty("mtime", entry.getValue().mtime);
            json.addProperty("hash", entry.getValue().hash);
            files.add(entry.getKey(), json);
        }
        JsonObject root = new JsonObject();
        root.add("files", files);
        try {
            Path parent = this.file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = this.file.resolveSibling(this.file.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
            Files.move(temp, this.file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            SaveGitMod.LOGGER.warn("写入文件哈希缓存失败", e);
        }
    }

    @Override
    public void close() {
        if (this.dirty) {
            this.save();
            this.dirty = false;
        }
    }
}
