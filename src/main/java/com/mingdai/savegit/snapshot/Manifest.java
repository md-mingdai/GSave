package com.mingdai.savegit.snapshot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mingdai.savegit.mca.RawMca;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * 一次快照的完整文件视图：文件 -> 区块 -> blob 哈希。
 * 全部使用 TreeMap，保证序列化确定，从而得到稳定的 manifestId。
 */
public final class Manifest {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    public final Map<String, FileEntry> files = new TreeMap<>();

    public Collection<FileEntry> getAllEntries() {
        return files.values();
    }

    public FileEntry getEntry(String path) {
        return files.get(path);
    }

    public void addMcaFile(String path, String dimension, String mcaType,
                           int regionX, int regionZ, Map<Integer, String> chunks) {
        FileEntry entry = new FileEntry();
        entry.type = "mca";
        entry.path = path;
        entry.dimension = dimension;
        entry.mcaType = mcaType;
        entry.regionX = regionX;
        entry.regionZ = regionZ;
        entry.chunks = new TreeMap<>(chunks);
        files.put(path, entry);
    }

    public void addWholeFile(String path, String dimension, String hash) {
        FileEntry entry = new FileEntry();
        entry.type = "whole";
        entry.path = path;
        entry.dimension = dimension;
        entry.hash = hash;
        files.put(path, entry);
    }

    public String computeHash() {
        return RawMca.hashBytes(toJson().getBytes(StandardCharsets.UTF_8));
    }

    public String toJson() {
        JsonObject root = new JsonObject();
        JsonObject filesJson = new JsonObject();
        for (Map.Entry<String, FileEntry> mapEntry : files.entrySet()) {
            FileEntry entry = mapEntry.getValue();
            JsonObject json = new JsonObject();
            addIfNotNull(json, "type", entry.type);
            addIfNotNull(json, "path", entry.path);
            addIfNotNull(json, "hash", entry.hash);
            addIfNotNull(json, "dimension", entry.dimension);
            addIfNotNull(json, "mcaType", entry.mcaType);
            if ("mca".equals(entry.type)) {
                json.addProperty("regionX", entry.regionX);
                json.addProperty("regionZ", entry.regionZ);
                JsonObject chunksJson = new JsonObject();
                for (Map.Entry<Integer, String> chunk : entry.chunks.entrySet()) {
                    chunksJson.addProperty(Integer.toString(chunk.getKey()), chunk.getValue());
                }
                json.add("chunks", chunksJson);
            }
            filesJson.add(mapEntry.getKey(), json);
        }
        root.add("files", filesJson);
        return GSON.toJson(root);
    }

    public static Manifest fromJson(String json) {
        Manifest manifest = new Manifest();
        JsonObject root = GSON.fromJson(json, JsonObject.class);
        if (root == null || !root.has("files") || !root.get("files").isJsonObject()) {
            return manifest;
        }
        JsonObject filesJson = root.getAsJsonObject("files");
        for (Map.Entry<String, JsonElement> mapEntry : filesJson.entrySet()) {
            if (!mapEntry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject json = mapEntry.getValue().getAsJsonObject();
            FileEntry entry = new FileEntry();
            entry.type = getString(json, "type");
            entry.path = mapEntry.getKey();
            entry.hash = getString(json, "hash");
            entry.dimension = getString(json, "dimension");
            entry.mcaType = getString(json, "mcaType");
            entry.regionX = getInt(json, "regionX");
            entry.regionZ = getInt(json, "regionZ");
            if (json.has("chunks") && json.get("chunks").isJsonObject()) {
                for (Map.Entry<String, JsonElement> chunk : json.getAsJsonObject("chunks").entrySet()) {
                    try {
                        entry.chunks.put(Integer.parseInt(chunk.getKey()), chunk.getValue().getAsString());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            manifest.files.put(mapEntry.getKey(), entry);
        }
        return manifest;
    }

    private static void addIfNotNull(JsonObject json, String name, String value) {
        if (value != null) {
            json.addProperty(name, value);
        }
    }

    private static String getString(JsonObject json, String name) {
        return json.has(name) && !json.get(name).isJsonNull() ? json.get(name).getAsString() : null;
    }

    private static int getInt(JsonObject json, String name) {
        return json.has(name) && !json.get(name).isJsonNull() ? json.get(name).getAsInt() : 0;
    }

    public static final class FileEntry {
        public String type;       // "mca" 或 "whole"
        public String path;       // 相对世界根目录
        public String hash;       // 整文件 blob 哈希（type=whole）
        public String dimension;
        public String mcaType;    // region / entities / poi
        public int regionX;
        public int regionZ;
        public Map<Integer, String> chunks = new TreeMap<>();  // 区块 index -> blob 哈希

        public boolean isMca() {
            return "mca".equals(type);
        }

        public boolean isWhole() {
            return "whole".equals(type);
        }
    }
}
