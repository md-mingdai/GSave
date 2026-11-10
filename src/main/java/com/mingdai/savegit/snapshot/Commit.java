package com.mingdai.savegit.snapshot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mingdai.savegit.mca.RawMca;

import java.nio.charset.StandardCharsets;

/**
 * 一次快照的提交元数据，构成以 HEAD 为起点的线性历史。
 */
public final class Commit {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    public String id;
    public String parent;
    public String manifestId;
    public long timestamp;
    public String message;
    public String worldName;
    public String mcVersion;
    /** 以下字段不参与 id 哈希，可随时修改。 */
    public String note = "";
    public boolean pinned;
    public long durationMs = -1L;

    public Commit() {
    }

    public static Commit create(String parent, String manifestId, long timestamp,
                                String message, String worldName, String mcVersion) {
        Commit commit = new Commit();
        commit.parent = parent;
        commit.manifestId = manifestId;
        commit.timestamp = timestamp;
        commit.message = message;
        commit.worldName = worldName;
        commit.mcVersion = mcVersion;
        commit.id = RawMca.hashBytes(commit.payloadJson().toString().getBytes(StandardCharsets.UTF_8));
        return commit;
    }

    public String toJson() {
        JsonObject json = payloadJson();
        json.addProperty("id", id);
        if (note != null && !note.isEmpty()) {
            json.addProperty("note", note);
        }
        if (pinned) {
            json.addProperty("pinned", true);
        }
        if (durationMs >= 0) {
            json.addProperty("durationMs", durationMs);
        }
        return GSON.toJson(json);
    }

    private JsonObject payloadJson() {
        JsonObject json = new JsonObject();
        if (parent != null) {
            json.addProperty("parent", parent);
        }
        json.addProperty("manifestId", manifestId);
        json.addProperty("timestamp", timestamp);
        json.addProperty("message", message == null ? "" : message);
        json.addProperty("worldName", worldName == null ? "" : worldName);
        json.addProperty("mcVersion", mcVersion == null ? "" : mcVersion);
        return json;
    }

    public static Commit fromJson(String jsonText) {
        JsonObject json = GSON.fromJson(jsonText, JsonObject.class);
        if (json == null) {
            return null;
        }
        Commit commit = new Commit();
        commit.id = getString(json, "id");
        commit.parent = getString(json, "parent");
        commit.manifestId = getString(json, "manifestId");
        commit.timestamp = json.has("timestamp") ? json.get("timestamp").getAsLong() : 0L;
        commit.message = getString(json, "message");
        commit.worldName = getString(json, "worldName");
        commit.mcVersion = getString(json, "mcVersion");
        String note = getString(json, "note");
        commit.note = note == null ? "" : note;
        commit.pinned = json.has("pinned") && json.get("pinned").getAsBoolean();
        commit.durationMs = json.has("durationMs") ? json.get("durationMs").getAsLong() : -1L;
        if (commit.id == null || commit.manifestId == null) {
            return null;
        }
        return commit;
    }

    private static String getString(JsonObject json, String name) {
        return json.has(name) && !json.get(name).isJsonNull() ? json.get(name).getAsString() : null;
    }

    public String shortId() {
        return id == null ? "????????" : id.substring(0, Math.min(8, id.length()));
    }
}
