package com.mingdai.savegit.snapshot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mingdai.savegit.mca.RawMca;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 快照时的模组环境指纹（Beta）：模组 id + 版本列表。
 * 用于恢复前提示“快照里存在、当前缺失”的模组，避免静默丢失模组数据。
 */
public final class WorldModEnvironment {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .create();

    public static final class Entry {
        public String id;
        public String version;

        public Entry() {
        }

        public Entry(String id, String version) {
            this.id = id;
            this.version = version;
        }
    }

    public final List<Entry> mods = new ArrayList<>();

    public static WorldModEnvironment capture() {
        WorldModEnvironment environment = new WorldModEnvironment();
        try {
            for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
                String id = mod.getMetadata().getId();
                String version;
                try {
                    version = mod.getMetadata().getVersion().getFriendlyString();
                } catch (Throwable t) {
                    version = "";
                }
                environment.mods.add(new Entry(id, version));
            }
        } catch (Throwable ignored) {
            // 模组 API 不可用时返回空环境，不影响快照本身
        }
        environment.mods.sort(Comparator.comparing(entry -> entry.id == null ? "" : entry.id));
        return environment;
    }

    public boolean isEmpty() {
        return this.mods.isEmpty();
    }

    public String fingerprint() {
        StringBuilder builder = new StringBuilder();
        for (Entry mod : this.mods) {
            builder.append(mod.id).append('@').append(mod.version).append('\n');
        }
        return RawMca.hashBytes(builder.toString().getBytes(StandardCharsets.UTF_8));
    }

    public String toJson() {
        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();
        for (Entry mod : this.mods) {
            JsonObject json = new JsonObject();
            json.addProperty("id", mod.id == null ? "" : mod.id);
            json.addProperty("version", mod.version == null ? "" : mod.version);
            array.add(json);
        }
        root.add("mods", array);
        return GSON.toJson(root);
    }

    public static WorldModEnvironment fromJson(String jsonText) {
        WorldModEnvironment environment = new WorldModEnvironment();
        if (jsonText == null || jsonText.isEmpty()) {
            return environment;
        }
        try {
            JsonObject root = GSON.fromJson(jsonText, JsonObject.class);
            if (root == null || !root.has("mods") || !root.get("mods").isJsonArray()) {
                return environment;
            }
            for (JsonElement element : root.getAsJsonArray("mods")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject json = element.getAsJsonObject();
                String id = json.has("id") ? json.get("id").getAsString() : "";
                String version = json.has("version") ? json.get("version").getAsString() : "";
                environment.mods.add(new Entry(id, version));
            }
        } catch (RuntimeException ignored) {
        }
        return environment;
    }

    /** 返回快照里存在、当前环境缺失的模组（id + 版本），用于恢复前提示。 */
    public List<String> missingFrom(WorldModEnvironment current) {
        Set<String> currentIds = new HashSet<>();
        for (Entry mod : current.mods) {
            if (mod.id != null) {
                currentIds.add(mod.id);
            }
        }
        List<String> missing = new ArrayList<>();
        for (Entry mod : this.mods) {
            if (mod.id == null || mod.id.isEmpty() || currentIds.contains(mod.id)) {
                continue;
            }
            missing.add(mod.version == null || mod.version.isEmpty()
                    ? mod.id : mod.id + " " + mod.version);
        }
        return missing;
    }
}
